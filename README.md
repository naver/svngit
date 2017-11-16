
**This project is not actively maintained.**
**Proceed at your own risk!**

-----

# SVNGit

A servlet implementation for SVN clients to access Git repositories

    SVN Client --> SVNGit Server (SVNGit + Jetty or Tomcat) --> Git repositories

**Note**: This is an early-stage experimental project.

## Feature

Currently, only these SVN commands are supported:

* checkout
* update
* log
* commit

## Our Goal

We will support most of basic SVN commands as Github does.

## Bulid & Install

Just add this dependency into your pom.xml to get the library from maven
central repository:

    <dependency>
        <groupId>com.navercorp</groupId>
        <artifactId>svngit</artifactId>
        <version>0.2.0</version>
    </dependency>

or build from source code and get target/svngit-VERSION.jar:

    git clone https://github.com/naver/svngit
    cd svngit
    mvn package

## Demo

1. Rename web.sample.xml to web.xml in src/main/webapp/WEB-INF and edit it

   If your Git repository is /path/to/gitroot/repo.git and you want to access the
repository with a url such as "http://localhost:8080/svngit/repo", set
SVNParentPath and the url pattern as follows:

        ...
        <servlet>
            <servlet-name>svngit</servlet-name>
            <servlet-class>com.navercorp.svngit.SVNGitServlet</servlet-class>
            <init-param>
                <param-name>SVNParentPath</param-name>
                <param-value>/path/to/gitroot</param-value>
            </init-param>
        </servlet>
    
        <servlet-mapping>
            <servlet-name>svngit</servlet-name>
            <url-pattern>/svngit/*</url-pattern>
        </servlet-mapping>
        ...

2. Run jetty

    $ mvn jetty:run

3. Test

    $ svn checkout http://localhost:8080/svngit/repo

## License

```
 Copyright 2015 NAVER Corp.
 
 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at
 
    http://www.apache.org/licenses/LICENSE-2.0
 
 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
```

All the source codes in package org.tmatesoft.svn.core.internal and
com.navercorp.svngit except class com.navercorp.svngit.SVNGitUtil, which originate from
SVNKit(http://svnkit.com/index.html), are covered by The TMate License.

Please check The TMate License in the NOTICE file before you use this SW.

## Contact

Please email <a href="mailto:eungjun.yi@navercorp.com">me</a>.

## Pull requests are welcomed!
