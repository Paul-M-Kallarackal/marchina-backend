# Marchina Backend

![Docker](https://img.shields.io/badge/docker-container-blue)
![Maven](https://img.shields.io/badge/build-Maven-green)
![Azure Container Apps](https://img.shields.io/badge/deployed-Azure%20Container%20Apps-blue)

Marchina Backend is a Spring Boot application that powers the Marchina AI Agents and Marchina Voice. It provides RESTful APIs for project and diagram management, and coordinates multiple AI agents (STT, Requirement Extraction, Diagram Generators, TTS) to build and validate technical diagrams. The service uses Microsoft Azure Container Apps for hosting, Azure Flexible PostgreSQL for the database, and Azure OpenAI & Cognitive Services for AI capabilities.


Quick Links to Marchina Repositories
- [Marchina Frontend](https://github.com/Paul-M-Kallarackal/marchina-frontend)
- [Marchina MCP Server](https://github.com/Paul-M-Kallarackal/marchina-mcp)

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Deployment Flow](#deployment-flow)
- [Agent Orchestration](#agent-orchestration)
- [Agent Flows](#agent-flows)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [Environment Variables](#environment-variables)

## Architecture Overview

This diagram illustrates how the frontend and backend interact with external services:

### Deployment Flow
```mermaid
flowchart TD
  F[MCP Server hosted on npx] -->|HTTPS|B
  A[Frontend: Azure Static Web App] -->|HTTPS| B[Backend: Spring Boot API]
  B -->|JDBC| C[(Azure Flexible PostgreSQL)]
  B -->|REST/SDK| D[Azure OpenAI Service]
  B -->|REST/SDK| E[Azure Cognitive Speech Services]
  style A fill:#f9f,stroke:#333,stroke-width:2px
  style B fill:#bbf,stroke:#333,stroke-width:2px
  style C fill:#bfb,stroke:#333,stroke-width:2px
  style D fill:#fbf,stroke:#333,stroke-width:2px
  style E fill:#ffb,stroke:#333,stroke-width:2px
  style F fill:#f9f,stroke:#333,stroke-width:2px
```

## Agent Orchestration and Flows

### 1. Text-based Project Creation Flow
```mermaid
flowchart LR
  U[User: Project Name & Description] --> PC[ProjectController]
  PC --> REQ[RequirementExtractorAgent]
  REQ --> MAIN[MainAgent]
  subgraph Generation
    MAIN --> ERD[ERDAgent]
    MAIN --> FLW[FlowchartAgent]
    MAIN --> CLS[ClassDiagramAgent]
    MAIN --> SEQ[SequenceDiagramAgent]
  end
  subgraph Persistence
    ERD & FLW & CLS & SEQ --> PService[ProjectService]
  end
  PService --> U2[User: Response JSON]
  style U fill:#f9f,stroke:#333,stroke-width:2px
  style PC fill:#bbf,stroke:#333,stroke-width:2px
  style REQ fill:#bfb,stroke:#333,stroke-width:2px
  style MAIN fill:#bbf,stroke:#333,stroke-width:2px
  style ERD fill:#fbf,stroke:#333,stroke-width:2px
  style FLW fill:#fbf,stroke:#333,stroke-width:2px
  style CLS fill:#fbf,stroke:#333,stroke-width:2px
  style SEQ fill:#fbf,stroke:#333,stroke-width:2px
  style PService fill:#bfa,stroke:#333,stroke-width:2px
  style U2 fill:#f9f,stroke:#333,stroke-width:2px
```

### 2. Voice-based Project Creation Flow
```mermaid
flowchart LR
  U[User Audio] --> STT[STTAgent]
  STT --> PC[AgentController]
  PC --> REQ[RequirementExtractorAgent]
  REQ --> MAIN[MainAgent]
  subgraph Looped Generation
    MAIN --> ERD[ERDAgent]
    MAIN --> FLW[FlowchartAgent]
    MAIN --> CLS[ClassDiagramAgent]
    MAIN --> SEQ[SequenceDiagramAgent]
  end
  MAIN -->|Speak Prompt| TTS[TTSAgent]
  TTS --> U
  subgraph Persistence
    ERD & FLW & CLS & SEQ --> PService[ProjectService]
    PService --> U2[User: Response & Audio]
  end
  style U fill:#f9f,stroke:#333,stroke-width:2px
  style STT fill:#bbf,stroke:#333,stroke-width:2px
  style PC fill:#bbf,stroke:#333,stroke-width:2px
  style REQ fill:#bfb,stroke:#333,stroke-width:2px
  style MAIN fill:#bbf,stroke:#333,stroke-width:2px
  style ERD fill:#fbf,stroke:#333,stroke-width:2px
  style FLW fill:#fbf,stroke:#333,stroke-width:2px
  style CLS fill:#fbf,stroke:#333,stroke-width:2px
  style SEQ fill:#fbf,stroke:#333,stroke-width:2px
  style TTS fill:#ffb,stroke:#333,stroke-width:2px
  style PService fill:#bfa,stroke:#333,stroke-width:2px
  style U2 fill:#f9f,stroke:#333,stroke-width:2px
```

### 3. Login Flow
```mermaid
flowchart TD
  subgraph Login Options
    G[Guest Login] --> GC[AuthController]
    U[User Login] --> UC[AuthController]
  end
  GC --> DB1[(PostgreSQL)] --> R1[Assign Guest Profile & Common Repo]
  UC --> DB2[(PostgreSQL)] --> R2[Verify Credentials]
  R1 & R2 --> Token[Issue JWT]
  Token --> U2[User]
  style G fill:#f9f,stroke:#333,stroke-width:2px
  style U fill:#f9f,stroke:#333,stroke-width:2px
  style GC fill:#bbf,stroke:#333,stroke-width:2px
  style UC fill:#bbf,stroke:#333,stroke-width:2px
  style DB1 fill:#bfb,stroke:#333,stroke-width:2px
  style DB2 fill:#bfb,stroke:#333,stroke-width:2px
  style Token fill:#bfa,stroke:#333,stroke-width:2px
```

### 4. MCP Server Flow
```mermaid
flowchart LR
  MCP[Npx MCP Server] -->|HTTP| MCPController
  MCPController --> REQ[RequirementExtractorAgent]
  REQ --> MAIN[MainAgent]
  subgraph Generation & Persistence
    MAIN --> ERD
    MAIN --> FLW
    MAIN --> CLS
    MAIN --> SEQ
    ERD & FLW & CLS & SEQ --> PService[ProjectService]
  end
  PService -->|Response| MCP
  style MCP fill:#f9f,stroke:#333,stroke-width:2px
  style MCPController fill:#bbf,stroke:#333,stroke-width:2px
  style REQ fill:#bfb,stroke:#333,stroke-width:2px
  style MAIN fill:#bbf,stroke:#333,stroke-width:2px
  style PService fill:#bfa,stroke:#333,stroke-width:2px
```

## Project Structure

```bash
marchina-backend/
├── src/
│   ├── main/
│   │   ├── java/com/marchina/
│   │   │   ├── agent/                # AI Agent implementations (STT, TTS, Main, ERD, etc.)
│   │   │   ├── controller/           # REST controllers (ProjectController, AgentController, etc.)
│   │   │   ├── model/                # Request & response DTOs
│   │   │   └── MarchinaApplication.java  # Spring Boot entry point
│   │   └── resources/
│   │       ├── application.properties  # Configuration
│   │       └── templates/              # Freemarker/Thymeleaf views (if any)
│   └── test/                         # Unit and integration tests
├── .github/workflows/                # GitHub Actions workflows for CI/CD
├── .azure/                           # Azure Resource Manager templates & settings
├── Dockerfile                        # Docker build for container
├── docker-compose.yml                # Local multi-container setup
├── pom.xml                           # Maven pom definition
└── mvnw, mvnw.cmd, .mvn/             # Maven wrapper
```

## Getting Started

### Prerequisites

- Java 17 or higher
- Maven 3.6 or higher
- Docker (for containerized deployment)
- Azure CLI (for Azure Container Apps)

### Local Development

1. Clone the repository:
   ```bash
   git clone https://github.com/<YOUR_GITHUB_ORG>/marchina-backend.git
   cd marchina-backend
   ```
2. Configure environment variables as below.
3. Build and run with Docker:
   ```bash
   mvn clean package -DskipTests
   docker-compose up --build
   ```
4. API will be available at `http://localhost:8080/api`

## Environment Variables

| Name                       | Description                                  |
|----------------------------|----------------------------------------------|
| SERVER_PORT                | Port for the Spring Boot application         |
| DB_URL                     | JDBC URL for PostgreSQL                      |
| DB_USERNAME                | Database username                            |
| DB_PASSWORD                | Database password                            |
| AZURE_OPENAI_KEY           | Azure OpenAI API key                         |
| AZURE_OPENAI_ENDPOINT      | Azure OpenAI endpoint URL                    |
| AZURE_SPEECH_KEY           | Azure Cognitive Speech key                   |
| AZURE_SPEECH_REGION        | Azure Cognitive Speech region                |

Set these in a `.env` file or environment prior to startup.

