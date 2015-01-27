CP = cp
JC = javac
JCFLAGS = -target 1.5 -source 1.5
JAR = jar
ARCHIVE = VncViewer.jar
MANIFEST = MANIFEST.MF
PAGES = index.vnc
INSTALL_DIR = /usr/local/vnc/classes

CLASSES = VncViewer.class RfbProto.class AuthPanel.class VncCanvas.class \
	  VncCanvas2.class \
	  OptionsFrame.class ClipboardFrame.class ButtonPanel.class \
	  DesCipher.class CapabilityInfo.class CapsContainer.class \
	  RecordingFrame.class SessionRecorder.class \
	  SocketFactory.class HTTPConnectSocketFactory.class \
	  HTTPConnectSocket.class ReloginPanel.class \
	  InStream.class MemInStream.class ZlibInStream.class

CLASSES+= DisplaySyncPoint.class FrameSequence.class FrameSequencer.class \
	  FrameTimeIterator.class FrameUpdate.class ScreenUpdateWatcher.class \
	  SyncSequence.class VncAnalyzer.class VncEventListener.class VncInputPlayback.class \
	  VncInputRecorder.class

SOURCES = VncViewer.java RfbProto.java AuthPanel.java VncCanvas.java \
	  VncCanvas2.java \
	  OptionsFrame.java ClipboardFrame.java ButtonPanel.java \
	  DesCipher.java CapabilityInfo.java CapsContainer.java \
	  RecordingFrame.java SessionRecorder.java \
	  SocketFactory.java HTTPConnectSocketFactory.java \
	  HTTPConnectSocket.java ReloginPanel.java \
	  InStream.java MemInStream.java ZlibInStream.java

SOURCES+= DisplaySyncPoint.java FrameSequence.java FrameSequencer.java \
	  FrameTimeIterator.java FrameUpdate.java ScreenUpdateWatcher.java \
	  SyncSequence.java VncAnalyzer.java VncEventListener.java VncInputPlayback.java \
	  VncInputRecorder.java

all: $(CLASSES) $(ARCHIVE)

$(CLASSES): $(SOURCES)
	$(JC) $(JCFLAGS) -O $(SOURCES)

$(ARCHIVE): $(CLASSES) $(MANIFEST)
	$(JAR) cfm $(ARCHIVE) $(MANIFEST) $(CLASSES)

install: $(CLASSES) $(ARCHIVE)
	$(CP) $(CLASSES) $(ARCHIVE) $(PAGES) $(INSTALL_DIR)

export:: $(CLASSES) $(ARCHIVE) $(PAGES)
	@$(ExportJavaClasses)

clean::
	$(RM) *.class *.jar

