/**
 * Original From SVNKit (http://svnkit.com/index.html)
 *
 * Modified by Naver Corp. (Author: Yi EungJun <eungjun.yi@navercorp.com>)
 */
package org.tmatesoft.svn.core.internal.io.fs;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.io.fs.SVNGitRepository;
import org.tmatesoft.svn.core.io.ISVNSession;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;

public class SVNGitRepositoryFactory extends SVNRepositoryFactory {
    @Override
    protected SVNRepository createRepositoryImpl(SVNURL url, ISVNSession options) {
        return new SVNGitRepository(url, options);
    }

    public static SVNRepository create(SVNURL url) {
        return new SVNGitRepository(url, null);
    }
}
