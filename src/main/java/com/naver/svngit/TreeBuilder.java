package com.navercorp.svngit;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.IOException;
import java.util.*;

public class TreeBuilder {
	private final Repository repo;
    SortedMap<String, ObjectId> addings = new TreeMap<>();
	DirCache dc = new InMemoryDirCache();
	private ObjectId baseTreeId;
	private Set<String> removings = new HashSet<>();
	private Map<String, String> movings = new HashMap<>();
	private Map<String, String> copyings = new HashMap<>();

	public TreeBuilder(Repository repository) {
		this.repo = repository;
	}

    public TreeBuilder setBaseTreeId(ObjectId baseTreeId) throws IOException {
		this.baseTreeId = baseTreeId;
        return this;
    }

	public Map<String, ObjectId> getAddings() {
		return addings;
	}

	public boolean hasBlob(String path) {
		// FIXME: should check the tree of baseTreeId.
		return addings.containsKey(path);
	}

    public TreeBuilder add(String path, ObjectId blobId) {
		addings.put(path, blobId);
		return this;
    }

	public TreeBuilder remove(String path) {
		removings.add(path);
		return this;
	}

	public TreeBuilder move(String sourcePath, String destinationPath) {
		movings.put(sourcePath, destinationPath);
		return this;
	}

	public TreeBuilder copy(String sourcePath, String destinationPath) {
		copyings.put(sourcePath, destinationPath);
		return this;
	}

	public class Result {
		private Map<String, DiffEntry.ChangeType> updates;
		private ObjectId treeId;

		public Map<String, DiffEntry.ChangeType> getUpdates() {
			return updates;
		}

		public ObjectId getTreeId() {
			return treeId;
		}

		public Result(Map<String, DiffEntry.ChangeType> updates, ObjectId treeId) {
			this.updates = updates;
			this.treeId = treeId;
		}
	}

	/**
	 * Write the tree into the repository and return the id.
	 *
	 * @return the object id of the tree
	 * @throws IOException
	 */
    public Result build() throws IOException {
		Map<String, DiffEntry.ChangeType> updates = new HashMap<>();
		DirCacheBuilder builder = dc.builder();
		Set<String> updatePaths = readAndUpdateIndex(builder);
		for(String path : updatePaths) {
			updates.put(path, DiffEntry.ChangeType.MODIFY);
		}
		// Add new blobs
		for (String path : addings.keySet()) {
			ObjectId blobId = addings.get(path);
			DirCacheEntry entry = new DirCacheEntry(path);
			entry.setFileMode(FileMode.REGULAR_FILE); // FIXME
			entry.setObjectId(blobId);
			builder.add(entry);
			updates.put(path, DiffEntry.ChangeType.ADD);
		}
		builder.commit();

		return new Result(updates, dc.writeTree(repo.getObjectDatabase().newInserter()));
	}

	// Imported from resetIndex method at
	// https://github.com/eclipse/jgit/blob/v3.5.1.201410131835-r/org.eclipse.jgit/src/org/eclipse/jgit/api/ResetCommand.java
	// and modified by Yi EungJun
	private Set<String> readAndUpdateIndex(DirCacheBuilder builder) throws IOException {
		TreeWalk walk = null;
		Set<String> updatedPaths = new HashSet<>();
		try {
			walk = new TreeWalk(repo);
			if (baseTreeId != null)
				walk.addTree(baseTreeId);
			else
				walk.addTree(new EmptyTreeIterator());
			walk.addTree(new DirCacheIterator(dc));
			walk.setRecursive(true);

			while (walk.next()) {
				final AbstractTreeIterator cIter = walk.getTree(0,
						AbstractTreeIterator.class);
				if (cIter == null) {
					// Not in commit, don't add to new index
					continue;
				}

				final String path = new String(walk.getRawPath());
				final DirCacheIterator dcIter = walk.getTree(1,
						DirCacheIterator.class);

				abstract class LazySourceEntry { abstract DirCacheEntry get(); }
				final LazySourceEntry sourceEntry = new LazySourceEntry() {
					private DirCacheEntry entry = null;
					public DirCacheEntry get() {
						if (entry != null) {
							return entry;
						}
						entry = new DirCacheEntry(path);
						entry.setFileMode(cIter.getEntryFileMode());
						if (dcIter != null && dcIter.idEqual(cIter)) {
							DirCacheEntry indexEntry = dcIter.getDirCacheEntry();
							entry.setLastModified(indexEntry.getLastModified());
							entry.setLength(indexEntry.getLength());
						}
						entry.setObjectIdFromRaw(cIter.idBuffer(), cIter.idOffset());
						return entry;
					}
				};

				// Update
				if (addings.containsKey(path)) {
					final DirCacheEntry newEntry = new DirCacheEntry(path);
					newEntry.copyMetaData(sourceEntry.get());
					newEntry.setObjectId(addings.get(path));
					builder.add(newEntry);
					addings.remove(path);
					updatedPaths.add(path);
				}

				// Rename or copy
				if (movings.containsKey(path) || copyings.containsKey(path)) {
					String newPath = movings.get(path);
					if (newPath == null) {
						newPath = copyings.get(path);
					}
					final DirCacheEntry newEntry = new DirCacheEntry(newPath);
					newEntry.copyMetaData(sourceEntry.get());
					builder.add(newEntry);
				}

				// Keep the source entry only if the path is neither deleted, renamed nor updated.
				if (!removings.contains(path) && !movings.containsKey(path) && !updatedPaths.contains(path)) {
					builder.add(sourceEntry.get());
				}
			}

			builder.commit();
		} finally {
			dc.unlock();
			if (walk != null)
				walk.release();
		}

		return updatedPaths;
	}

}
