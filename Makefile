JC = javac -target 1.5 -source 1.5

all:
	$(JC) -O *.java

clean:
	$(RM) *.class

