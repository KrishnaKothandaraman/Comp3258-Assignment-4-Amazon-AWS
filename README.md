# Comp3258-Assignment-4-Amazon-AWS

** Code Structure **
```tree
.
├── README.md
├── ec2-worker
│   ├── pom.xml
│   └── src
│       ├── main
│       │   └── java
│       │       └── com
│       │           └── ec2projects
│       │               └── App.java
│       └── test
│           └── java
│               └── com
│                   └── ec2projects
│                       └── AppTest.java
├── server
│   ├── pom.xml
│   └── src
│       ├── main
│       │   ├── java
│       │   │   └── com
│       │   │       └── projects
│       │   │           ├── App.java
│       │   │           └── AppServlet.java
│       │   └── webapp
│       │       └── WEB-INF
│       │           └── web.xml
│       └── test
│           └── java
│               └── com
│                   └── projects
│                       └── AppTest.java
└── static files
    └── index.html
```

## Main components

### ec2-worker

This directory contains the class App.java which implements the ec2-worker part of this project. It listens in the inbox queue, processes images and sends it to the outbox queue

### Server

This directory has 2 files
** App.java **: Implements the image transformation and queueing logic
** AppServlet.java**: Implements the servlet which is used with apache tomcat to make this service available to the world

### Static files

This directory contains the simple web interface to use this service, index.html

## Run instructions

### Ec2-worker

1. `cd ec2-worker/`
2. `mvn clean install`
3. `java -jar target/Ec2-Worker-1.0-SNAPSHOT-jar-with-dependencies.jar`

Before step 3, you can transfer this file to your ec2 instance and run it there using the `scp` command

### Server

**_NOTE:_** You must have set up apache tomcat on your ec2 instance or local computer already!

1. `mvn clean install`
2. Transfer `target/Comp3258_Assignment_4-1.0-SNAPSHOT.war` to the `webapps` dir of your Apache Tomcat installation
3. Start your tomcat server


### Index.html

Open index.html from your browser and use the service!
