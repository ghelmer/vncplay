#!/usr/bin/perl
#
# Front-end wrapper script for VNCplay

use File::Basename;
my $tooldir = dirname($0);

my %options = ();
my @args = ();
while ($#ARGV >= 0) {
    my $arg = shift @ARGV;
    if ($arg =~ /^-/) {
	my $val = shift @ARGV;
	$options{$arg} = $val;
    } else {
	push @args, $arg;
    }
}

my $op = shift @args;
if (!(defined $op)) {
    print
	"Usage: vncplay [options] command ...\n",
	"  commands:  record            Record a VNC session\n",
	"             play              Play back a VNC session\n",
	"             autoplay          Playback without display\n",
	"             analyze           Analyze replayed sessions\n",
	"  options:   -pwfile filename  Read password from a file\n";
    exit(1);
}

$ENV{CLASSPATH} .= ":" . $tooldir;
my @vncviewer_args = ( "encoding", "hextile" );

if ($op eq 'record' || $op eq 'play' || $op eq 'autoplay') {
    my $server = shift @args;
    die "Usage: vncplay (record|play|autoplay) host:port ...\n" unless defined $server;
    my ($host, $port) = split /:/, $server;
    push @vncviewer_args, "host", $host, "port", $port;
}

if (defined $options{'-pwfile'}) {
    my $pwfile = $options{'-pwfile'};
    my $pwfd;
    open($pwfd, $pwfile) || die "Cannot open password file $pwfile\n";
    my $pw = <$pwfd>;
    close($pwfd);

    $pw =~ s/[\r\n]*$//;
    push @vncviewer_args, "password", $pw;
}

if ($op eq 'record') {
    my ($tracefile) = @args;
    die "Usage: vncplay record host:port tracefile\n" unless defined $tracefile;
    print "Recording into trace log file $tracefile\n";

    system("java", "VncViewer", @vncviewer_args,
				"autorecord", "yes",
				"tracefile", $tracefile);
} elsif ($op eq 'play' || $op eq 'autoplay') {
    my ($tracefile, $logfile) = @args;
    die "Usage: vncplay (play|autoplay) host:port tracefile vnclogfile\n" unless defined $tracefile && defined $logfile;
    print "Playing back trace from $tracefile\n";

    my $xvncpid = -1;
    if ($op eq 'autoplay') {
	$xvncpid = start_xvnc();
    }

    system("java", "VncViewer", @vncviewer_args,
				"autoplay", "yes",
				"tracefile", $tracefile,
				"recordfile", $logfile);
    kill 15, $xvncpid if $xvncpid > 0;
} elsif ($op eq 'analyze') {
    my (@rfbfiles) = @args;
    die "Usage: vncplay analyze vnclogfile1 vnclogfile2 ...\n" unless $#rfbfiles >= 1;
    my $xvncpid = start_xvnc();

    my @analyzer_args = ();
    foreach my $vnclog (@rfbfiles) {
	push @analyzer_args, $vnclog;
	push @analyzer_args, $vnclog . ".aux";
    }

    system("java", "-Xmx1700m", "VncAnalyzer", @analyzer_args);

    kill 15, $xvncpid;
} else {
    print "Unknown operation $op\n";
    exit(1);
}

exit(0);

sub start_xvnc {
    my $d;
    for ($d = 20; $d < 100; $d++) {
	next if -e "/tmp/.X${d}-lock";
	next if -e "/tmp/.X11-unix/X${d}";

	$ENV{DISPLAY}=":${d}.0";
	last;
    }

    my $xvncpid = fork();
    die "Cannot fork" if $xvncpid < 0;
    if ($xvncpid == 0) {
	exec "Xvnc", "-auth", "/dev/null",
		    "-nolisten", "tcp",
		    $ENV{DISPLAY};
	die "Cannot start Xvnc -- is it installed?\n";
    }

    sleep 1;
    system("xhost >/dev/null 2>&1") and die "Cannot connect to Xvnc\n";
    return $xvncpid;
}
