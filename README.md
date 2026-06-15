# Glit – Miniature Version Control System

**Glit** is a lightweight, Java-based version control system inspired by Git. The project focuses on implementing the fundamental mechanics behind modern VCS solutions, including object storage, SHA-1 hashing, compression, branch management, commit history tracking, and working directory restoration.

The primary goal of Glit is educational: to demonstrate how version control systems operate internally while maintaining a clean and modular Java architecture.

---

## Features

* Repository initialization (`glit init`)
* Staging area management (`glit add`)
* Commit creation with metadata and history tracking
* Branch creation and switching
* Working tree restoration
* SHA-1-based object identification
* Compressed object storage (zlib)
* Commit history browsing
* File and directory tree snapshots
* Automatic working directory synchronization during checkout
* Basic merge support with conflict detection

---

## System Requirements

| Component                    | Version                  |
|------------------------------| ------------------------ |
| Operating System             | Linux (Bash environment) |
| Java                         | JDK 17+                  |
| Build Tool (for dev-version) | Apache Maven 3.6+        |

---

## Installation & Setup

Glit provides an automated installation script that builds the project and configures a global `glit` command.

### Use-only Installation

1. Download user_package.tar.gz from install_package directory.
2. Extract files and run script

```bash
tar xzf user_package.tar.gz
chmod +x glit/glit-setup.sh && ./glit/glit-setup.sh
```

3. Your Glit is ready to use.


### Dev Installation

1. Clone or download the repository.
2. Navigate to the project root directory.
3. Make the setup script executable and run it:

```bash
chmod +x glit-setup.sh
./glit-setup.sh
```

4. Your Glit is ready to use. (The generated JAR file will be available in the `target/` directory.)

---

## Project Structure

```text
src/main/java/glit/
├── cli/
│   ├── Call.java
│   └── GlitController.java
│
├── exceptions/
│   ├── GlitException.java
│   ├── MergeConflictException.java
│   └── MissingRepositoryException.java
│
├── merge/
│   ├── FileMerger.java
│   └── TreeMerger.java
│
├── model/
│   ├── Blob.java
│   ├── Commit.java
│   ├── Tree.java
│   ├── TreeEntry.java
│   ├── GlitIndex.java
│   └── IndexEntry.java
│
├── service/
│   └── Repository.java
│
├── storage/
│   ├── ObjectReader.java
│   └── ObjectWriter.java
│
└── util/
    ├── HashUtils.java
    └── IndexUtils.java
```

### Package Responsibilities

#### cli

Handles command-line parsing and routes user commands to the repository service layer.

#### exceptions

Contains custom exceptions used throughout the application.

#### merge

Implements merge algorithms for files and directory trees.

#### model

Defines repository data structures such as commits, trees, blobs, and index entries.

#### service

Contains the core repository engine responsible for executing user commands.

#### storage

Provides low-level object persistence, compression, and retrieval.

#### util

Helper classes for hashing and index manipulation.

#### merge

Implements merge algorithms for files and directory trees.

#### model

## Example Workflow

The following example demonstrates the most common Glit operations.

```bash
# Create a new project
mkdir my-awesome-project
cd my-awesome-project

# Initialize repository
glit init

# Create first file
echo "public class Main {}" > Main.java

# Check repository state
glit status

# Stage and commit changes
glit add Main.java
glit commit -m "Initial commit"

# Create and switch to a feature branch
glit checkout -b feature_login

# Add new functionality
echo "Login logic goes here" > Login.java

# Stage and commit changes
glit add .
glit commit -m "Added login functionality"

# View repository history
glit log

# Switch back to the main branch
glit checkout main

# Merge feature branch
glit merge feature_login

# Verify repository state
glit status
```

### Additional Commands

List all branches:

```bash
glit branch
```

Create a branch without switching to it:

```bash
glit branch feature-api
```

Display the contents of a stored object:

```bash
glit cat-file <object-hash>
```

(dev-only) Rebuild project after changes:

```bash
glit rebuild
```

---

## Internal Repository Layout

After initialization, Glit creates the following repository structure:

```text
.glit/
├── HEAD
├── index
├── refs/
│   └── heads/
└── objects/
    ├── aa/
    ├── b3/
    └── ...
```

### HEAD

Stores the currently checked-out branch reference.

### refs/heads/

Contains branch pointers.

### objects/

Stores compressed repository objects identified by SHA-1 hashes.

### index

Represents the staging area.

---

## Technical Details

### Object Types

#### Blob

Represents file content.

#### Tree

Represents a directory snapshot.

#### Commit

Stores:

* commit message
* author information
* timestamp
* parent commit reference
* root tree hash

---

### Storage Model

Every object is:

1. Serialized
2. Hashed using SHA-1
3. Compressed using zlib
4. Stored inside `.glit/objects`

Example:

```text
SHA1:
e69de29bb2d1d6434b8b29ae775ad8c2e48c5391

Stored as:
.glit/objects/e6/9de29bb2d1d6434b8b29ae775ad8c2e48c5391
```

This closely follows Git's object storage model.

---

## Known Issues & Technical Limitations

### Year 2038 Problem

Glit currently stores timestamps using a 32-bit Unix timestamp representation, similarly to historical Git implementations.

This approach will overflow on:

```text
19 January 2038
03:14:07 UTC
```

### Planned Solution

Future versions should migrate to:

* 64-bit timestamps
* modern date/time APIs (`java.time`)
* backward-compatible timestamp serialization

to ensure long-term repository compatibility.

---

## License

This project was created for educational purposes and to explore the internal architecture of distributed version control systems.
