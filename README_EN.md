<div align="center">

# 🎯 MyPalantir

**Ontology-Based Data Model Management Platform**

[English](README_EN.md) | [中文](README.md)

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-18-blue.svg)](https://reactjs.org/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.0-blue.svg)](https://www.typescriptlang.org/)

A data model management platform inspired by Palantir Foundry Ontology design philosophy. It decouples business concepts from physical data sources through an Ontology abstraction layer, providing unified query interfaces, semantic data access capabilities, and intelligent data analysis tools.

[Features](#-key-features) • [Quick Start](#-quick-start) • [Documentation](#-related-documentation) • [License](#-license)

</div>

---

## ✨ Key Features

- 🎨 **Semantic Querying** - Query using business concepts (e.g., "Vehicle", "Toll Station") instead of table/column names
- 🔌 **Data Source Agnostic** - Map the same business concept to different physical data sources (PostgreSQL, MySQL, H2, Neo4j, File System, etc.)
- 🔗 **Relationship Abstraction** - Abstract object relationships through LinkType, supporting multiple physical implementation patterns
- 🚀 **Unified Interface** - Provide a unified query DSL that shields differences in underlying data sources
- 🤖 **AI-Enhanced** - Integrate LLM for natural language querying (Text-to-DSL)
- 📊 **Metric System** - Built-in atomic and derived metric engines supporting multi-dimensional analysis
- 🔍 **Data Governance** - Cross-data-source data consistency comparison tools
- 🌐 **Federated Query** - Support cross-data-source federated queries without manual data movement
- 📈 **Lineage Analysis** - Support lineage queries in instance relationship graphs to track data flow
- 🎯 **ETL Integration** - Deep integration with external ETL systems, supporting automatic ETL model definition building

## 🚀 Quick Start

### Prerequisites

- **Java 17+**
- **Maven 3.6+**
- **Node.js 18+** (for building Web UI)

### Installation & Running

```bash
# 1. Clone the repository
git clone https://github.com/caochun/mypalantir.git
cd mypalantir

# 2. Build backend
mvn clean install

# 3. Build frontend
cd web && npm install && npm run build && cd ..

# 4. Run the service
mvn spring-boot:run
```

Visit http://localhost:8080 to view the Web UI.

### Configuration

Edit `src/main/resources/application.properties`:

```properties
server.port=8080

# Ontology model configuration
ontology.model=schema
schema.file.path=./ontology/${ontology.model}.yaml

# Data storage configuration
storage.type=hybrid  # file | neo4j | hybrid

# LLM configuration (for natural language queries)
llm.api.key=${LLM_API_KEY:your-api-key}
llm.api.url=${LLM_API_URL:https://api.deepseek.com/v1/chat/completions}
```

For more configuration details, refer to the [Configuration Section](#configuration).

## 📖 Table of Contents

- [Core Philosophy](#core-philosophy)
  - [Ontology-Driven Data Model](#ontology-driven-data-model)
  - [Design Principles](#design-principles)
- [System Architecture](#system-architecture)
  - [Overall Architecture](#overall-architecture)
  - [Query Engine Architecture](#query-engine-architecture)
  - [LinkType Mapping Modes](#linktype-mapping-modes)
  - [Query Processing Flow](#query-processing-flow)
- [Technical Architecture](#technical-architecture)
  - [Technology Stack](#technology-stack)
  - [Core Modules](#core-modules)
  - [Data Flow](#data-flow)
- [Quick Start](#quick-start)
  - [Prerequisites](#prerequisites)
  - [Installation & Running](#installation--running)
  - [Configuration](#configuration)
- [Core Features](#core-features)
- [Feature Introduction](#feature-introduction)
- [Query Examples](#query-examples)
- [Project Structure](#project-structure)
- [API Reference](#api-reference)
- [Data Storage](#data-storage)
- [Important Notes](#important-notes)
- [Related Documentation](#related-documentation)
- [License](#license)

## Core Philosophy

### Ontology-Driven Data Model

MyPalantir's core philosophy is **decoupling business concepts from physical storage**, establishing mappings between business semantics and underlying data sources through the Ontology layer.

```
Business Concept Layer (Ontology)
    ↓ mapping
Physical Data Layer (Database/File System)
```

**Core Advantages:**
- **Semantic Querying**: Query using business concepts (e.g., "Vehicle", "Toll Station") instead of table/column names
- **Data Source Agnostic**: Map the same business concept to different physical data sources (PostgreSQL, MySQL, H2, Neo4j, File System, etc.)
- **Relationship Abstraction**: Abstract object relationships through LinkType, supporting multiple physical implementation patterns
- **Unified Interface**: Provide a unified query DSL that shields differences in underlying data sources
- **AI-Enhanced**: Integrate LLM for natural language querying (Text-to-DSL)
- **Metric System**: Built-in atomic and derived metric engines supporting multi-dimensional analysis
- **Data Governance**: Cross-data-source data consistency comparison tools

### Design Principles

1. **Concept-First**: All queries and operations are based on concepts defined in Ontology, not physical table structures
2. **Flexible Mapping**: Support multiple data source mapping patterns to adapt to different database designs
3. **Query Optimization**: Apache Calcite-based query optimizer that automatically generates efficient SQL
4. **Type Safety**: Complete Schema validation mechanism ensuring data model consistency

## System Architecture

### Overall Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                 Application Layer                            │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │  Web UI      │  │  REST API    │  │  Query DSL   │      │
│  │  (React)     │  │  (Spring)    │  │  (JSON)      │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│              Intelligence Layer                               │
│  ┌──────────────────────┐    ┌──────────────────────────┐    │
│  │  Metric Engine       │    │  LLM Service             │    │
│  │  (Metric Calculation) │    │  (NLQ Conversion)         │    │
│  └──────────────────────┘    └──────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│              Ontology Layer                                  │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  Schema Definition (YAML)                            │  │
│  │  - ObjectType                                         │  │
│  │  - LinkType                                           │  │
│  │  - Property                                           │  │
│  │  - DataSourceMapping                                 │  │
│  └──────────────────────────────────────────────────────┘  │
│                            ↓                                 │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  Query Engine                                         │  │
│  │  - OntologyQuery DSL → RelNode → SQL                 │  │
│  │  - Apache Calcite Optimizer                          │  │
│  │  - Automatic JOIN Optimization                       │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│              Data Source Layer                               │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐        │
│  │  JDBC        │  │  File System │  │  Neo4j       │        │
│  │  (Database)  │  │  (JSON)      │  │  (Graph DB)  │        │
│  └──────────────┘  └──────────────┘  └──────────────┘        │
└─────────────────────────────────────────────────────────────┘
```

### Query Engine Architecture

The query engine is the core of the system, implementing the complete transformation flow from Ontology Query DSL to physical SQL:

```
OntologyQuery (JSON/YAML)
    ↓ [QueryParser]
OntologyQuery (Java Object)
    ↓ [ExecutionRouter]  # Routing decision: single source vs federated
    ├─→ Single Data Source Path
    │   ↓ [RelNodeBuilder]
    │   Calcite RelNode (Relational Algebra Tree)
    │   ↓ [Calcite Optimizer]
    │   Optimized RelNode
    │   ↓ [OntologyRelToSqlConverter]
    │   SQL (Physical Database Query)
    │   ↓ [JDBC Execution]
    │   QueryResult (Result Set)
    │
    └─→ Cross-Data-Source Path (Federated Query)
        ↓ [FederatedCalciteRunner]
        Calcite Federated Execution Plan
        ↓ [Multi-Data-Source Parallel Query + Calcite Join]
        QueryResult (Result Set)
```

**Execution Routing Mechanism:**
- `ExecutionRouter` automatically analyzes data sources involved in the query
- Single data source queries: Use traditional SQL path (optimal performance)
- Cross-data-source queries: Use Calcite federated execution (supports cross-database JOIN)

#### Key Components

1. **OntologyQuery DSL**
   - GraphQL-style query language
   - Supports `object`, `select`, `filter`, `links`, `group_by`, `metrics`, etc.
   - Completely based on Ontology concepts, no physical table/column names involved

2. **RelNodeBuilder**
   - Converts OntologyQuery to Calcite RelNode (relational algebra tree)
   - Handles JOIN, Filter, Project, Aggregate, Sort, Limit operations
   - Automatically handles LinkType JOIN logic
   - Supports unidirectional queries for directed relationships and bidirectional queries for undirected relationships

3. **OntologySchemaFactory**
   - Converts Ontology Schema to Calcite Schema
   - Creates Calcite Tables for each ObjectType and LinkType
   - Handles property name to column name mapping

4. **JdbcOntologyTable**
   - Calcite Table implementation responsible for reading data from JDBC data sources
   - Handles mapping between Ontology property names and database column names
   - Supports type conversion (e.g., TIMESTAMP → Long)

5. **OntologyRelToSqlConverter**
   - Custom SQL converter
   - Maps Ontology names in Calcite-generated SQL to database physical names
   - Handles table and column name references

6. **ExecutionRouter**
   - Query execution routing decision maker
   - Automatically analyzes the number of data sources involved in queries
   - Single data source uses SQL path, cross-data-source uses federated execution path

7. **FederatedCalciteRunner**
   - Cross-data-source federated query executor
   - Mounts multiple data sources based on Calcite's JdbcSchema
   - Supports cross-data-source JOIN, aggregation, etc.
   - Automatically pushes down filters and projections to each data source, reducing data transfer

## Technical Architecture

### Technology Stack

**Backend:**
- **Java 17**: Modern Java features
- **Spring Boot 3.2.0**: Application framework
- **Apache Calcite 1.37.0**: Query optimization engine
- **Jackson**: JSON/YAML processing
- **H2 Database**: Local test database

**Frontend:**
- **React 18 + TypeScript**: Modern UI framework
- **Vite**: Fast build tool
- **Tailwind CSS**: Utility-first CSS framework
- **React Router**: Single-page application routing
- **react-force-graph-2d**: Force-directed graph visualization library
- **Heroicons**: Icon library

## Core Features

### 1. Intelligent Metric Management

Provides complete metric definition and calculation engine, supporting building from atomic metrics to complex composite metrics.

**Key Features:**
- **Atomic Metrics**: Basic metrics based on direct SQL queries
- **Derived Metrics**: Derived metrics calculated from formulas
- **Composite Metrics**: Multi-dimensional analysis combining multiple metrics
- **Visual Builder**: Graphical metric building interface
- **Batch Calculation**: Supports batch metric calculation based on time ranges and dimensions

### 2. Natural Language Query

Integrates LLM capabilities to lower the threshold for data querying, achieving "conversation as query".

**Key Features:**
- **Text-to-DSL**: Automatically converts natural language to OntologyQuery DSL
- **Intelligent Context**: Automatically generates prompts based on current Ontology Schema
- **Interactive Interface**: Chat-style query interface with real-time preview of conversion results and data results
- **Debug Mode**: Supports viewing intermediate results of the conversion process

### 3. Data Reconciliation

Used to verify data consistency between different data sources or models, ensuring data quality.

**Key Features:**
- **Dual-Mode Comparison**:
  - **By Table**: Directly compare differences between two physical tables
  - **By Model**: Compare object data under different workspaces/models
- **Difference Report**: Detailed display of fully matched, value inconsistent, source-only, and target-only data
- **Field Mapping**: Supports custom mapping between source and target fields

### 4. ETL Model Building & Integration

Provides deep integration with external ETL systems, supporting automatic ETL model definition building and ETL task creation.

**Key Features:**
- **Automatic ETL Model Building**: Based on mapping relationships between Ontology object types and physical tables, automatically builds ETL model definitions that meet ETL system requirements
- **dome-scheduler Integration**: Automatically calls external ETL scheduling system to create ETL definitions
- **dome-datasource Integration**: Automatically obtains table structure and field information from external data sources
- **Batch Building Support**: Supports batch building of ETL models for multiple object types
- **Intelligent Mapping**: Automatically handles field mapping, primary key mapping, data type conversion, etc.

## Query Examples

### Basic Query

```json
{
  "object": "Vehicle",
  "select": ["LicensePlate", "VehicleType", "OwnerName"],
  "filter": [["=", "VehicleType", "Small Passenger Car"]],
  "limit": 10
}
```

### Relationship Query

```json
{
  "object": "Vehicle",
  "select": ["LicensePlate"],
  "links": [{
    "name": "Has",
    "select": ["MediaNumber", "MediaType", "BindTime"]
  }]
}
```

### Aggregation Query

```json
{
  "object": "TollStation",
  "links": [{"name": "HasTollRecord"}],
  "filter": [
    ["=", "Province", "Jiangsu"],
    ["between", "HasTollRecord.TollTime", "2024-01-01", "2024-01-31"]
  ],
  "group_by": ["Name"],
  "metrics": [["sum", "HasTollRecord.Amount", "TotalAmount"]]
}
```

**Supported Aggregation Functions:**
- `sum`: Sum
- `avg`: Average
- `count`: Count
- `min`: Minimum
- `max`: Maximum

## Data Storage

The system supports multiple data storage backends, selectable via the `storage.type` configuration:

### File Storage Mode (file)

Uses local file system to store instance and relationship data in JSON format.

**Use Cases:**
- Development and testing environments
- Small-scale data
- Scenarios that don't require high-performance queries

### Neo4j Graph Database Mode (neo4j)

Uses Neo4j as the storage backend, providing high-performance graph data query capabilities.

**Advantages:**
- Efficient graph traversal queries
- Supports complex relationship queries
- Suitable for large-scale relationship data

### Hybrid Storage Mode (hybrid) - Recommended

Combines the advantages of relational databases and Neo4j for optimal performance and functionality balance.

**Storage Strategy:**
- **Relational Database**: Stores complete instance detailed data (all properties)
- **Neo4j**: Stores relationship data and key fields (configured via `storage.neo4j.fields.*`)

**Advantages:**
- Excellent relationship query performance (Neo4j)
- Flexible detailed data queries (relational database)
- Supports large-scale data storage
- Flexible field storage configuration based on business needs

## Important Notes

### Workspace

- System workspaces (containing `workspace`, `database`, `table`, `column`, `mapping`) automatically hide management functions
- When workspace is empty, the navigation bar doesn't display any object types or relationship types
- Query builder filters available object types based on workspace

### Relationship Query Limitations

- **Directed Relationships**: Can only query from source_type to target_type, cannot query in reverse
- **Undirected Relationships**: Support bidirectional queries, can query from either end
- Query builder automatically filters available relationship types based on relationship direction

### Performance Optimization

- Graph views default to limiting node and relationship counts, adjustable via settings panel
- In workspace mode, limit values are automatically increased
- Using batch APIs can reduce HTTP requests and improve loading performance

## Related Documentation

- [CHANGELOG.md](./CHANGELOG.md) - Detailed feature change records
- [CHANGELOG_SUMMARY.md](./CHANGELOG_SUMMARY.md) - Concise feature change summary
- [web/README.md](./web/README.md) - Frontend project documentation

## 📄 License

This project is licensed under the [MIT License](LICENSE).

---

<div align="center">

**Made with ❤️ by MyPalantir Contributors**

[⭐ Star us on GitHub](https://github.com/caochun/mypalantir) • [📖 Documentation](./docs/) • [🐛 Report Bug](https://github.com/caochun/mypalantir/issues)

</div>





