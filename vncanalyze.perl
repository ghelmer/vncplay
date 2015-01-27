#!/usr/bin/perl

use strict;

my ($type, $vncplay_analyze_file) = @ARGV;
if (!(defined $type && defined $vncplay_analyze_file)) {
    print
	"Usage: vncanalyze type vncplay_analyze_output\n",
	"  types:  raw-latency, cdf, median\n";
    exit(1);
}

my @screen_ts = ();
my $num_traces = 0;
my @input_ts = ();

open(VAOUT, $vncplay_analyze_file) or die "cannot open $vncplay_analyze_file";
while (<VAOUT>) {
    s/[\r\n]*$//;
    if (/^Sync log (\d+): (.*)/) {
	my $num = $1;
	if ($num > $num_traces) {
	    $num_traces = $num;
	}
    }

    if (/^== (\d+)@(\d+) matches with (\d+)@(\d+) .*/) {
	my ($ats, $anum, $bts, $bnum) = ($1, $2, $3, $4);

	if ($anum == $bnum && $ats != $bts) {
	    print STDERR "Suspicious line: $_\n";
	}

	push @{$screen_ts[$bnum]}, $bts;
    }

    if (/^input_ts: (\d+): (\d+)/) {
	push @{$input_ts[$1]}, $2;
    }
}
close(VAOUT);

# here $num_traces is actually the index of the last trace, not the
# count of them
$num_traces++;

if ($num_traces < 2) {
    die "Insufficient number of traces $num_traces!\n";
}

for (my $i = 0; $i < $num_traces; $i++) {
    @{$screen_ts[$i]} = sort { $a <=> $b } @{$screen_ts[$i]};
}

#
# Here we want to find, for each screen update, the last input event
# that happened before this screen update in all runs
#
my @input_match_idx = ();

for (my $i = 0; $i < $num_traces; $i++) {
    my $ts_idx = 0;

    foreach my $ts (@{$screen_ts[$i]}) {
	my $input_idx = find_floor_idx_in_seq($ts, @{$input_ts[$i]});
	my $other_idx = $input_match_idx[$ts_idx];
	if ( (!(defined $other_idx)) || ($input_idx < $other_idx) ) {
	    $input_match_idx[$ts_idx] = $input_idx;
	}

	$ts_idx++;
    }
}

#
# Now we calculate the latency between screen updates and the corresponding
# input event...
#

my @lag = ();
for (my $i = 0; $i < $num_traces; $i++) {
    my @input_ts_i = @{$input_ts[$i]};
    my $ts_idx = 0;

    foreach my $ts (@{$screen_ts[$i]}) {
	my $input_ts_idx = $input_match_idx[$ts_idx++];
	next if $input_ts_idx == -1;

	my $input_ts = $input_ts_i[$input_ts_idx];
	my $lag = $ts - $input_ts;
	push @{$lag[$i]}, $lag;
    }
}

if ($type eq 'median') {
    print "Median latencies:";
    for (my $i = 0; $i < $num_traces; $i++) {
	my @lats = sort { $a <=> $b } @{$lag[$i]};
	print " ", $lats[$#lats / 2];
    }
    print "\n";
} elsif ($type eq 'raw-latency') {
    my $idx = 0;
    print "# Screen-update-index latency-from-trace0 latency-from-trace1 ...\n";
    foreach my $foo (@{$lag[0]}) {
	print $idx;
	for (my $i = 0; $i < $num_traces; $i++) {
	    print " ", $lag[$i][$idx];
	}
	print "\n";
	$idx++;
    }
} elsif ($type eq 'cdf') {
    my $totcount;
    my %cdf = ();
    $cdf{0} = ();
    for (my $i = 0; $i < $num_traces; $i++) {
	my @lats = sort { $a <=> $b } @{$lag[$i]};
	$totcount = $#lats + 1;
	my $thislat = 0.0;
	foreach my $lat (@lats) {
	    $thislat++;
	    my $pct = 100.0 * $thislat / $totcount;
	    $cdf{$lat}{$i} = $pct;
	}
    }

    my %cur_cdf_value = ();
    for (my $i = 0; $i < $num_traces; $i++) {
	$cur_cdf_value{$i} = 0;
    }

    foreach my $lat (sort { $a <=> $b } keys %cdf) {
	foreach my $i (keys %{$cdf{$lat}}) {
	    $cur_cdf_value{$i} = $cdf{$lat}{$i};
	}
	print "$lat";
	for (my $i = 0; $i < $num_traces; $i++) {
	    print " ", $cur_cdf_value{$i}; 
	}
	print "\n";
    }
} else {
    print "Unknown analysis type: $type\n";
}

sub find_floor_idx_in_seq {
    my ($ts, @seq) = @_;

    my $lastidx = -1;
    my $idx = 0;
    foreach my $s (@seq) {
	last if $s >= $ts;
	$lastidx = $idx;
	$idx++;
    }
    return $lastidx;
}

