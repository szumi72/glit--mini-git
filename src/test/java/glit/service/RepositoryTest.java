
// ==========================================
// 5. TESTY DLA GLIT CHECKOUT
// ==========================================

//    @Test
//    void testCheckoutSwitchesToExistingBranchAndRestoresRepositoryFiles(@TempDir Path tempDir) throws Exception {
//        String originalDir = System.getProperty("user.dir");
//        System.setProperty("user.dir", tempDir.toString());
//        try {
//            Files.createDirectories(tempDir.resolve(".glit/refs/heads"));
//            Files.createFile(tempDir.resolve(".glit/index"));
//            Files.writeString(tempDir.resolve(".glit/HEAD"), "ref: refs/heads/main");
//            Files.writeString(tempDir.resolve(".glit/refs/heads/main"), "commitMainHash");
//            Files.writeString(tempDir.resolve(".glit/refs/heads/feature"), "commitFeatureHash");
//
//            Path oldFile = tempDir.resolve("old.txt");
//            Files.writeString(oldFile, "old content");
//
//            Repository.REPOSITORY_PATH = tempDir;
//            Repository.INDEX_PATH = tempDir.resolve(".glit/index");
//
//            Commit mockCommitFeature = mock(Commit.class);
//            Commit mockCommitMain = mock(Commit.class);
//            Tree mockTreeFeature = mock(Tree.class);
//            Tree mockTreeMain = mock(Tree.class);
//            Blob mockBlobFeature = mock(Blob.class);
//
//            when(mockCommitFeature.getTreeHash()).thenReturn("treeFeatureHash");
//            when(mockCommitMain.getTreeHash()).thenReturn("treeMainHash");
//            when(mockTreeFeature.getEntries()).thenReturn(new ArrayList<>(List.of(new glit.model.TreeEntry("100644", "blobFeatureHash", "new.txt"))));
//            when(mockTreeMain.getEntries()).thenReturn(new ArrayList<>(List.of(new glit.model.TreeEntry("100644", "blobMainHash", "old.txt"))));
//            when(mockBlobFeature.getContent()).thenReturn("new content".getBytes());
//
//            try (MockedConstruction<ObjectReader> mockedReader = mockConstruction(ObjectReader.class, (mock, context) -> {
//                when(mock.readObject(anyString())).thenAnswer(invocation -> {
//                    String hash = invocation.getArgument(0, String.class);
//                    return switch (hash) {
//                        case "commitFeatureHash" -> mockCommitFeature;
//                        case "commitMainHash" -> mockCommitMain;
//                        case "treeFeatureHash" -> mockTreeFeature;
//                        case "treeMainHash" -> mockTreeMain;
//                        case "blobFeatureHash" -> mockBlobFeature;
//                        default -> null;
//                    };
//                });
//            })) {
//                Call call = new Call("checkout", null, List.of("feature"));
//                Repository.checkout(call);
//            }
//
//            assertFalse(Files.exists(oldFile), "Old branch file should be removed after checkout");
//            Path newFile = tempDir.resolve("new.txt");
//            assertTrue(Files.exists(newFile), "New branch file should be restored from tree");
//            assertEquals("new content", Files.readString(newFile));
//            assertEquals("ref: refs/heads/feature", Files.readString(tempDir.resolve(".glit/HEAD")).trim());
//            assertTrue(outContent.toString().contains("Switched to a branch 'feature'"));
//        } finally {
//            System.setProperty("user.dir", originalDir);
//        }
//    }

//    @Test
//    void testCheckoutCreatesAndSwitchesToNewBranchWhenFlagBIsUsed(@TempDir Path tempDir) throws Exception {
//        String originalDir = System.getProperty("user.dir");
//        System.setProperty("user.dir", tempDir.toString());
//        try {
//            Files.createDirectories(tempDir.resolve(".glit/refs/heads"));
//            Files.createFile(tempDir.resolve(".glit/index"));
//            Files.writeString(tempDir.resolve(".glit/HEAD"), "ref: refs/heads/main");
//            Files.writeString(tempDir.resolve(".glit/refs/heads/main"), "commitMainHash");
//
//            Repository.REPOSITORY_PATH = tempDir;
//            Repository.INDEX_PATH = tempDir.resolve(".glit/index");
//
//            Call call = new Call("checkout", List.of("b"), List.of("feature"));
//            Repository.checkout(call);
//
//            assertTrue(Files.exists(tempDir.resolve(".glit/refs/heads/feature")));
//            assertEquals("commitMainHash", Files.readString(tempDir.resolve(".glit/refs/heads/feature")).trim());
//            assertEquals("ref: refs/heads/feature", Files.readString(tempDir.resolve(".glit/HEAD")).trim());
//            assertTrue(outContent.toString().contains("Switched to a new branch 'feature'"));
//        } finally {
//            System.setProperty("user.dir", originalDir);
//        }
//    }

//    @Test
//    void testCheckoutPrintsErrorWhenIndexHasStagedFiles(@TempDir Path tempDir) throws Exception {
//        String originalDir = System.getProperty("user.dir");
//        System.setProperty("user.dir", tempDir.toString());
//        try {
//            Files.createDirectories(tempDir.resolve(".glit/refs/heads"));
//            Files.writeString(tempDir.resolve(".glit/HEAD"), "ref: refs/heads/main");
//            Files.writeString(tempDir.resolve(".glit/refs/heads/main"), "commitMainHash");
//            Files.writeString(tempDir.resolve(".glit/refs/heads/feature"), "commitFeatureHash");
//
//            Repository.REPOSITORY_PATH = tempDir;
//            Repository.INDEX_PATH = tempDir.resolve(".glit/index");
//
//            Path a = tempDir.resolve("a.txt");
//            Path b = tempDir.resolve("b.txt");
//            Files.writeString(a, "A");
//            Files.writeString(b, "B");
//
//            Call call = new Call("add", List.of(), List.of(a, b));
//
//            Files.writeString(b, "BBB");
//
//            Repository.add(call);
//
//            call = new Call("checkout", List.of(), List.of("feature"));
//            Repository.checkout(call);
//
//            assertEquals("ref: refs/heads/main", Files.readString(tempDir.resolve(".glit/HEAD")).trim());
//            assertTrue(outContent.toString().contains("Files tracked, but not staged for commit:"));
//        } finally {
//            System.setProperty("user.dir", originalDir);
//        }
//    }

// ==========================================
// 6. TESTY DLA GLIT MERGE
// ==========================================
//
//    @Test
//    void testMergeFindsBaseTreeAndBuildsMergedTreeAndClearsIndex(@TempDir Path tempDir) throws Exception {
//        String originalDir = System.getProperty("user.dir");
//        System.setProperty("user.dir", tempDir.toString());
//        try {
//            Path repoDir = tempDir;
//            Files.createDirectories(repoDir.resolve(".glit/refs/heads"));
//            Files.createDirectories(repoDir.resolve(".glit/objects"));
//            Path indexPath = repoDir.resolve(".glit/index");
//            Files.writeString(indexPath, "staged content");
//            Files.writeString(repoDir.resolve(".glit/HEAD"), "ref: refs/heads/main");
//            Files.writeString(repoDir.resolve(".glit/refs/heads/main"), "ourCommitHash");
//            Files.writeString(repoDir.resolve(".glit/refs/heads/their"), "theirCommitHash");
//
//            Repository.REPOSITORY_PATH = repoDir;
//            Repository.INDEX_PATH = indexPath;
//
//            Commit mockOurCommit = mock(Commit.class);
//            Commit mockTheirCommit = mock(Commit.class);
//            Commit mockBaseCommit = mock(Commit.class);
//            Tree mockYourTree = mock(Tree.class);
//            Tree mockTheirTree = mock(Tree.class);
//            Tree mockBaseTree = mock(Tree.class);
//            Tree mockMergedTree = mock(Tree.class);
//            Blob mockMergedBlob = mock(Blob.class);
//
//            when(mockOurCommit.getTreeHash()).thenReturn("ourTreeHash");
//            when(mockOurCommit.getParentHash()).thenReturn("baseCommitHash");
//            when(mockTheirCommit.getTreeHash()).thenReturn("theirTreeHash");
//            when(mockTheirCommit.getParentHash()).thenReturn("baseCommitHash");
//            when(mockBaseCommit.getTreeHash()).thenReturn("baseTreeHash");
//            when(mockBaseCommit.getParentHash()).thenReturn("");
//            when(mockYourTree.getEntries()).thenReturn(new ArrayList<>());
//            when(mockTheirTree.getEntries()).thenReturn(new ArrayList<>());
//            when(mockBaseTree.getEntries()).thenReturn(new ArrayList<>());
//            when(mockMergedTree.getEntries()).thenReturn(new ArrayList<>(List.of(new glit.model.TreeEntry("100644", "mergedBlobHash", "merged.txt"))));
//            when(mockMergedBlob.getContent()).thenReturn("merged content".getBytes());
//
//            try (MockedConstruction<ObjectReader> mockedReader = mockConstruction(ObjectReader.class, (mock, context) -> {
//                when(mock.readObject("ourCommitHash")).thenReturn(mockOurCommit);
//                when(mock.readObject("theirCommitHash")).thenReturn(mockTheirCommit);
//                when(mock.readObject("baseCommitHash")).thenReturn(mockBaseCommit);
//                when(mock.readObject("baseTreeHash")).thenReturn(mockBaseTree);
//                when(mock.readObject("ourTreeHash")).thenReturn(mockYourTree);
//                when(mock.readObject("theirTreeHash")).thenReturn(mockTheirTree);
//                when(mock.readObject("mergedTreeHash")).thenReturn(mockMergedTree);
//                when(mock.readObject("mergedBlobHash")).thenReturn(mockMergedBlob);
//            })) {
//                try (MockedConstruction<TreeMerger> mockedMerger = mockConstruction(TreeMerger.class, (mock, context) -> {
//                    when(mock.mergeTree("baseTreeHash", "ourTreeHash", "theirTreeHash")).thenReturn("mergedTreeHash");
//                })) {
//                    assertEquals("main", Repository.getCurrentBranchName(), "Merge should not run in detached HEAD mode");
//
//                    Call call = new Call("merge", List.of(), List.of("their"));
//                    Repository.merge(call);
//
//                    assertTrue(outContent.toString().contains("Merged succesfully"), "Merge should report success");
//                    verify(mockedMerger.constructed().get(0), times(1)).mergeTree("baseTreeHash", "ourTreeHash", "theirTreeHash");
//                    verify(mockBaseCommit, times(1)).getTreeHash();
//                }
//            }
//
//            assertNotEquals(0, Files.size(indexPath), "Index should not be cleared after merge");
//            Path mergedFile = repoDir.resolve("merged.txt");
//            assertTrue(Files.exists(mergedFile), "Merged tree should restore merged file to working directory");
//            assertEquals("merged content", Files.readString(mergedFile));
//            assertEquals("ref: refs/heads/main", Files.readString(repoDir.resolve(".glit/HEAD")).trim());
//            assertNotEquals("ourCommitHash", Files.readString(repoDir.resolve(".glit/refs/heads/main")).trim(), "Branch ref should be updated to merged commit hash");
//        } finally {
//            System.setProperty("user.dir", originalDir);
//        }
//    }
