# 🤖 Coding Agent — Spring AI + Ollama

A fully local, REST API-based AI coding assistant built with **Spring AI** and **Ollama**.
No cloud. No API keys. Your code never leaves your machine.

---

## 📋 Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Prerequisites](#prerequisites)
- [Getting Started](#getting-started)
- [Configuration](#configuration)
- [API Reference](#api-reference)
- [Sample Requests](#sample-requests)
- [Dynamic Agent Roles](#dynamic-agent-roles)
- [Logging](#logging)

---

## Overview

This project is a Spring Boot application that exposes a REST API to interact with a locally running LLM via Ollama.
The agent has access to tools for reading files, searching code, running shell commands, and analyzing uploaded files — making it a powerful assistant for working with any codebase.

---

## ✨ Features

- 📂 **File System Access** — Read and write files directly from your codebase
- 🔎 **Code Search** — Search through files using grep and glob patterns
- ⚙️ **Shell Command Execution** — Run terminal commands from within the agent
- 📤 **File Upload API** — Upload any file and ask questions about it
- 🎭 **Dynamic Agent Roles** — Switch roles (code reviewer, security auditor, technical writer) per request via payload
- 🔧 **Configurable System Prompt** — Set a default role in `application.properties`, override it per request
- 📊 **Structured Logging** — Detailed logs with intent detection, response time, and file metadata
- 🏠 **100% Local** — Runs entirely on your machine via Ollama

---

## 🛠️ Tech Stack

| Technology | Version | Purpose |
|---|---|---|
| Spring Boot | 3.4.4 | Application framework |
| Spring AI | 1.1.0 | AI/LLM integration layer |
| Ollama | Latest | Local LLM runtime |
| Spring AI Community Tools | 0.4.2 | FileSystem, Grep, Glob, Shell tools |
| Java | 21 | Language |
| Maven | 3.x | Build tool |

---

## 📁 Project Structure

```
src/main/java/com/dev/codingagent/
├── CodingagentApplication.java     ← Spring Boot entry point
├── CodingAgentController.java      ← REST endpoints
├── CodingAgentService.java         ← Core agent logic
├── AgentRequest.java               ← Request DTO
└── AgentResponse.java              ← Response DTO

src/main/resources/
└── application.properties          ← Configuration
```

---

## ✅ Prerequisites

Before running the application, make sure you have:

- **Java 21** installed
- **Maven 3.x** installed
- **Ollama** installed and running — [https://ollama.com](https://ollama.com)
- A model pulled in Ollama that supports tool calling

### Pull a recommended model

```bash
# Best for coding tasks
ollama pull qwen2.5-coder:7b

# Or use llama3.2
ollama pull llama3.2

# Verify it's available
ollama list
```

> ⚠️ Make sure the model you pull supports **tool calling**. Check the [Ollama model page](https://ollama.com/search) and look for the **Tools** tag.

---

## 🚀 Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/your-username/codingagent.git
cd codingagent
```

### 2. Make sure Ollama is running

```bash
ollama serve
```

### 3. Build the project

```bash
mvn clean install
```

### 4. Run the application

```bash
mvn spring-boot:run
```

Or run the jar directly:

```bash
java -jar target/codingagent-0.0.1-SNAPSHOT.jar
```

### 5. Run on a custom port

```bash
java -jar target/codingagent-0.0.1-SNAPSHOT.jar --server.port=9090
```

The API will be available at `http://localhost:8090` (or your configured port).

---

## ⚙️ Configuration

All configuration lives in `src/main/resources/application.properties`:

```properties
# ── Ollama Config ──────────────────────────────────────────────
spring.ai.ollama.base-url=http://localhost:11434
spring.ai.ollama.chat.model=llama3.2
spring.ai.ollama.chat.options.temperature=0.2
spring.ai.ollama.chat.options.num-ctx=8192

# ── Server Config ──────────────────────────────────────────────
server.port=8090

# ── Agent Config ───────────────────────────────────────────────
agent.system.prompt=You are a helpful coding assistant. You have access to tools \
  for reading files, searching code, running shell commands, \
  and editing files. Use them to help the user with their codebase.

# ── File Upload Config ─────────────────────────────────────────
spring.servlet.multipart.enabled=true
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB

# ── Logging Config ─────────────────────────────────────────────
logging.level.com.dev.codingagent=INFO
logging.level.org.springframework.ai=WARN
logging.pattern.console=%d{HH:mm:ss} [%level] %msg%n
```

---

## 📡 API Reference

### Base URL
```
http://localhost:8090/api/agent
```

### Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/health` | Check if the agent is running |
| `GET` | `/info` | Get agent runtime info (OS, Java version, working directory) |
| `POST` | `/chat` | Chat with the agent, optionally passing a file path |
| `POST` | `/chat/upload` | Upload a file and ask questions about it |

---

## 📬 Sample Requests

### Health Check

```bash
curl http://localhost:8090/api/agent/health
```

**Response:**
```
🤖 Coding Agent is running!
```

---

### Agent Info

```bash
curl http://localhost:8090/api/agent/info
```

**Response:**
```json
{
  "workingDir": "C:\\Projects\\codingagent",
  "javaVersion": "21.0.2",
  "os": "Windows 11",
  "status": "running"
}
```

---

### Chat — General Prompt

```bash
curl --location 'http://localhost:8090/api/agent/chat' \
--header 'Content-Type: application/json' \
--data '{
    "prompt": "give me an overview of this codebase"
}'
```

---

### Chat — With File Path

```bash
curl --location 'http://localhost:8090/api/agent/chat' \
--header 'Content-Type: application/json' \
--data '{
    "prompt": "explain what this file does",
    "filePath": "C:/Projects/codingagent/src/main/java/com/dev/codingagent/CodingAgentService.java"
}'
```

> 💡 Use forward slashes `/` in file paths to avoid JSON escape issues on Windows.

---

### Chat — With Custom Role

```bash
curl --location 'http://localhost:8090/api/agent/chat' \
--header 'Content-Type: application/json' \
--data '{
    "prompt": "review this code for issues",
    "filePath": "C:/Projects/codingagent/src/main/java/com/dev/codingagent/CodingAgentController.java",
    "systemPrompt": "You are a senior code reviewer. Identify bugs, code smells, and suggest improvements."
}'
```

---

### Chat — File Upload

```bash
curl --location 'http://localhost:8090/api/agent/chat/upload' \
--form 'prompt="document this file with JavaDoc comments"' \
--form 'file=@"C:/Projects/codingagent/src/main/java/com/dev/codingagent/CodingAgentService.java"'
```

---

### Chat — File Upload With Custom Role

```bash
curl --location 'http://localhost:8090/api/agent/chat/upload' \
--form 'prompt="find any security vulnerabilities in this file"' \
--form 'file=@"C:/Projects/codingagent/src/main/java/com/dev/codingagent/CodingAgentController.java"' \
--form 'systemPrompt="You are a security expert. Review code for vulnerabilities like SQL injection, XSS, and insecure endpoints."'
```

---

### Sample Response

```json
{
  "response": "This file is the main service class for the coding agent...",
  "workingDir": "C:\\Projects\\codingagent\\target",
  "responseTimeMs": 3241,
  "promptNumber": 2,
  "fileName": "CodingAgentService.java"
}
```

---

## 🎭 Dynamic Agent Roles

One of the most powerful features is the ability to switch the agent's role per request using the `systemPrompt` field.

| Role | systemPrompt value |
|---|---|
| Default Coding Assistant | *(leave empty — uses application.properties)* |
| Code Reviewer | `"You are a senior code reviewer. Identify bugs, code smells, and improvements."` |
| Security Auditor | `"You are a security expert. Find vulnerabilities, injection risks, and insecure patterns."` |
| Performance Engineer | `"You are a performance engineer. Identify bottlenecks, memory leaks, and optimization opportunities."` |
| Technical Writer | `"You are a technical writer. Generate clear JavaDoc and inline comments for all methods."` |
| Test Engineer | `"You are a QA engineer. Write comprehensive unit and integration tests for the given code."` |

---

## 📊 Logging

The application produces structured logs for every request:

```
15:45:01 [INFO] ═══════════════════════════════════════════════
15:45:01 [INFO] 🤖  Coding Agent Initializing...
15:45:01 [INFO] 📂  Working Directory : C:\Projects\codingagent
15:45:01 [INFO] ☕  Java Version      : 21.0.2
15:45:01 [INFO] 🖥️  OS               : Windows 11
15:45:01 [INFO] ✅  ChatClient built successfully
15:45:01 [INFO] 🔧  Tools registered: FileSystemTools, GrepTool, GlobTool, ShellTools

15:46:10 [INFO] 📨  [Q-1] Prompt received
15:46:10 [INFO] 💬  Input: explain what this file does
15:46:10 [INFO] 📄  File path provided: C:/Projects/codingagent/src/main/java/...
15:46:10 [INFO] 🔍  Intent: FILE READ — looking inside C:\Projects\codingagent
15:46:10 [INFO] ⏳  Sending prompt to LLM...
15:46:13 [INFO] ✅  Response received in 2843ms
15:46:13 [INFO] 📝  Response length: 876 characters
```

---

## 🤝 Contributing

Pull requests are welcome! For major changes, please open an issue first to discuss what you would like to change.

---

## 📄 License

This project is open source and available under the [MIT License](LICENSE).

---

> Built with ❤️ using Spring AI + Ollama | #LearningInPublic
