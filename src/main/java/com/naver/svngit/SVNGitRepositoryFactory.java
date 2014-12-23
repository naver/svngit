package com.naver.svngit;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.io.ISVNSession;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;

/**
 * Created by nori on 14. 12. 23.
 */
public class SVNGitRepositoryFactory extends SVNRepositoryFactory {
    @Override
    protected SVNRepository createRepositoryImpl(SVNURL url, ISVNSession options) {
        return new SVNGitRepository(url, options);
    }

    public static SVNRepository create(SVNURL url) throws SVNException {
        return new SVNGitRepository(url, null);
    }
}
