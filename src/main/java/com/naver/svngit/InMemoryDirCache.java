package com.navercorp.svngit;

import org.eclipse.jgit.dircache.DirCache;

public class InMemoryDirCache extends DirCache {
    /**
     * Create a new in-core index representation.
     * <p/>
     * The new index will be empty.
     */
    public InMemoryDirCache() {
        super(null, null);
    }

    public void write() {
        // Do nothing
    }

    public boolean commit() {
        // Do nothing
        return true;
    }
}
