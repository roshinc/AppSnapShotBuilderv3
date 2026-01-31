# Scan Data Processor

A Java library that transforms raw scanner output (`ProjectInfo`) into pre-processed scan data (`ScanData`) optimized for build-time tree construction. Part of the application dependency tree system.

## Overview

This library provides components for:

- **Processing scan results**: Transform raw scanner output into optimized data structures
- **Building reverse lookup maps**: Enable efficient method resolution
- **Analyzing call chains**: Determine ownership of service/function usages
- **Pre-computing dependencies**: Build direct and transitive dependency graphs for tree construction

## Features

- Process and analyze Maven project scan results
- Build reverse lookup maps for method resolution
- Determine ownership of service/function usages through call chain analysis
- Pre-compute entry point children for direct dependency lookup
- Generate public method dependencies for transitive resolution
- Queue/topic resolution for event publishers

## Technology Stack

- **Java**: Version 21
- **Build Tool**: Maven
- **JSON Serialization**: Jackson 2.17.0
- **Testing**: JUnit 5.10.2

## Project Structure

```
src/
├── main/java/gov/nystax/nimbus/codesnap/
│   ├── services/scanner/domain/       # Scanner output domain models
│   │   ├── ProjectInfo.java           # Raw scanner output
│   │   ├── ServiceUsage.java
│   │   ├── FunctionUsage.java
│   │   ├── EventPublisherUsage.java
│   │   └── ...
│   ├── services/processor/            # Processing logic
│   │   ├── ScanDataProcessor.java     # Main processor
│   │   ├── ServiceScanService.java
│   │   ├── ServiceScanRecordFactory.java
│   │   ├── dao/                       # Data access objects
│   │   └── domain/                    # Processed data models
│   │       ├── ScanData.java          # Processed output
│   │       └── ...
│   └── services/builder/              # App snapshot builder
│       ├── AppSnapshotBuilder.java
│       ├── AppSnapshotService.java
│       ├── TransitiveResolver.java
│       ├── QueueNameResolver.java
│       └── domain/
└── test/java/                         # JUnit tests
```

## Requirements

- Java 21 or higher
- Maven 3.6+

## Build

```bash
# Compile the project
mvn clean compile

# Run tests
mvn test

# Build the JAR
mvn clean package
```

## Usage

### Processing Scan Data

```java
import gov.nystax.nimbus.codesnap.services.processor.ScanDataProcessor;
import gov.nystax.nimbus.codesnap.services.processor.domain.ScanData;
import gov.nystax.nimbus.codesnap.services.scanner.domain.ProjectInfo;

// Create processor instance
ScanDataProcessor processor = new ScanDataProcessor();

// Process raw scanner output
ProjectInfo projectInfo = // ... load from scanner
ScanData scanData = processor.process(projectInfo);

// Use the processed data
Map<String, String> functionMappings = scanData.getFunctionMappings();
Map<String, EntryPointDependencies> entryPointChildren = scanData.getEntryPointChildren();
```

### Key Domain Objects

| Class | Description |
|-------|-------------|
| `ProjectInfo` | Raw output from code scanner containing service/function metadata |
| `ScanData` | Pre-processed data optimized for dependency tree construction |
| `ServiceUsage` | Represents a service invocation with its call chain |
| `FunctionUsage` | Represents a function invocation with its call chain |
| `EntryPointDependencies` | Dependencies (functions, services, topics) for an entry point |

## License

Proprietary