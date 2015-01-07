# SVNGit

A servlet implementation for SVN clients to access Git repositories

    SVN Client --> SVNGit Server (SVNGit + Jetty or Tomcat) --> Git repositories

*Note*: This is an early-stage experimental project.

## Feature

Currently, only these SVN commands are supported:

* checkout
* update
* log

## Goal

We will support most of basic SVN commands as Github does.

## Build

    $ mvn jar:jar

## Demo

1. Rename web.sample.xml to web.xml in src/main/webapp/WEB-INF and edit it

If your Git repository is /path/to/gitroot/repo.git and you want to access the
repository with a url such as "http://localhost:8080/svngit/repo", set
SVNParentPath and the url pattern as follows:

    ...
    <servlet>
        <servlet-name>svngit</servlet-name>
        <servlet-class>com.naver.svngit.SVNGitServlet</servlet-class>
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

## Pull requests are welcomed!
