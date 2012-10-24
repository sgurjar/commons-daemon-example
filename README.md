commons-daemon-example
======================

an example of using common daemon to run java program as daemon.
    
* download commons-daemon-1.0.10-native-src.tar.gz from http://archive.apache.org/dist/commons/daemon/source/

* configure and build jsvc
    
    ./configure --with-java=/usr/local/opt/oracle/product/11.1.0/jdk
    make

__NOTE__
ibm jdk distributed with websphere 7 doesnt have jni_md.h in its 
jdk/include dir, thats why commons-daemon cant be built using it.

* write a java program that needs to be run a daemon, DirWatcher.java
 
* we will need commons-daemon-1.0.10.jar


      |-- README                                
      |-- bin
      |   |-- dirwatcher                          daemon ctrl script (start,stop,status)
      |   `-- jsvc                                commons daemon native program
      |-- lib
      |   |-- commons-daemon-1.0.10.jar           commons daemon java lib  
      |   `-- dirwatcher.jar                      sample program jar file
      |-- mkjar                                   script to compile and build jar file
      `-- src
          `-- DirWatcher.java                     java program source    

* build and tested on RHEL