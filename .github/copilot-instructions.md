# Copilot Instructions for Scan Data Processor

## Project Overview

This is a **Scan Data Processor** for the application dependency tree system. The project transforms raw scanner output (`ProjectInfo`) into pre-processed scan data (`ScanData`) optimized for build-time tree construction.

### Core Purpose
- Process and analyze Maven project scan results
- Build reverse lookup maps for method resolution
- Determine ownership of service/function usages through call chain analysis
- Pre-compute entry point children for direct dependency lookup
- Generate public method dependencies for transitive resolution

## Technology Stack

- **Java**: Version 21
- **Build Tool**: Maven
- **Key Dependencies**:
  - Jackson (2.17.0) for JSON serialization
  - JUnit 5 (5.10.2) for testing

## Project Structure

```
src/
├── main/java/gov/nystax/nimbus/codesnap/
│   ├── services/scanner/domain/     # Scanner output domain models
│   │   ├── ProjectInfo.java          # Raw scanner output
│   │   ├── ServiceUsage.java
│   │   ├── FunctionUsage.java
│   │   └── ...
│   └── services/processor/          # Processing logic
│       ├── ScanDataProcessor.java    # Main processor
│       ├── ServiceScanService.java
│       ├── ServiceScanRecordFactory.java
│       ├── dao/                      # Data access objects
│       └── domain/                   # Processed data models
│           └── ScanData.java         # Processed output
└── test/java/                       # JUnit tests
```

## Build and Test Commands

### Build
```bash
mvn clean compile
```

### Run Tests
```bash
mvn test
```

### Package
```bash
mvn clean package
```

## Code Style and Conventions

### General Guidelines
- Use **Java 21 features** where appropriate
- Follow **standard Java naming conventions**
- Write **comprehensive JavaDoc** for public classes and methods
- Use `java.util.logging.Logger` for logging

### Domain Model Patterns
- Use Jackson annotations (`@JsonProperty`) for JSON serialization
- Domain classes in `scanner.domain` represent raw scanner output
- Domain classes in `processor.domain` represent processed/optimized data
- Keep domain models immutable where possible

### Processing Logic
- The `ScanDataProcessor` is the main entry point for transformations
- Build reverse lookup maps for efficient method/service resolution
- Analyze call chains to determine usage ownership
- Pre-compute dependencies for optimization

### Error Handling
- Validate inputs with `IllegalArgumentException` for null checks
- Use `Logger` to log warnings and errors at appropriate levels
- Provide meaningful error messages

## Testing Guidelines

- Write unit tests using **JUnit 5**
- Test files should be in `src/test/java` mirroring the main structure
- Use descriptive test method names (e.g., `testProcessValidProjectInfo`)
- Test edge cases: null inputs, empty collections, malformed data
- Mock external dependencies when necessary

## Domain-Specific Context

### Key Concepts
- **ProjectInfo**: Raw output from code scanner containing service/function metadata
- **ScanData**: Pre-processed data optimized for dependency tree construction
- **Entry Points**: Service methods or functions that serve as tree roots
- **Service Invocations**: Calls from one service to another
- **Function Invocations**: Calls to specific functions/methods
- **Usage Tracking**: Determining which entry point owns each usage

### Common Operations
- **Method Resolution**: Use reverse lookup maps (methodImplementationToInterface)
- **Ownership Determination**: Trace call chains from entry points
- **Dependency Mapping**: Build direct and transitive dependency graphs
- **Queue/Topic Resolution**: Map event publishers to their topics

## Important Notes for Changes

- When modifying processor logic, ensure data transformations remain consistent
- Update tests when changing domain models or processing algorithms
- Maintain backward compatibility with existing `ProjectInfo` JSON format
- Keep performance in mind - this processor handles large codebases
- Validate that lookup maps are correctly populated before use
