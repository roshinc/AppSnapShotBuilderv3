# Build Instructions

## Prerequisites

This project requires **Java 21** or higher to compile and run.

### Verify Your Java Version

Check your current Java version:

```bash
java -version
```

You should see output similar to:
```
openjdk version "21.0.x" ...
```

### Installing Java 21

If you don't have Java 21 installed, you can install it using one of the following methods:

#### Using SDKMAN (Recommended)
```bash
sdk install java 21.0.1-tem
sdk use java 21.0.1-tem
```

#### Using Package Managers

**macOS (Homebrew):**
```bash
brew install openjdk@21
```

**Ubuntu/Debian:**
```bash
sudo apt update
sudo apt install openjdk-21-jdk
```

**Windows:**
Download and install from [Adoptium](https://adoptium.net/) or [Oracle](https://www.oracle.com/java/technologies/downloads/#java21).

### Setting JAVA_HOME

Make sure your `JAVA_HOME` environment variable points to Java 21:

**Linux/macOS:**
```bash
export JAVA_HOME=/path/to/java21
export PATH=$JAVA_HOME/bin:$PATH
```

**Windows:**
```cmd
set JAVA_HOME=C:\path\to\java21
set PATH=%JAVA_HOME%\bin;%PATH%
```

## Building the Project

### Clean and Compile

```bash
mvn clean compile
```

### Run Tests

```bash
mvn test
```

### Package JAR

```bash
mvn clean package
```

The compiled JAR will be located at:
```
target/scan-data-processor-1.0.0.jar
```

## Troubleshooting

### Error: "invalid target release: 21"

This error means Maven is trying to use a Java version older than 21. 

**Solution:** Ensure Java 21 is installed and active:
1. Check your Java version: `java -version`
2. Set JAVA_HOME to point to Java 21
3. Verify Maven is using the correct Java: `mvn -version`

### Verification

To verify everything is set up correctly:

```bash
# Check Java version
java -version

# Check Maven is using the correct Java
mvn -version

# Compile the project
mvn clean compile
```

All commands should complete successfully without errors.

## Build Output

A successful build will show:
```
[INFO] BUILD SUCCESS
[INFO] Total time: X.XXX s
```

Any compilation errors will be displayed with file names and line numbers for easy debugging.
