# AI coding assistant guidelines

This file provides guidance to AI coding assistant when working with code in this repository.

## Project overview

See @README.md for a project overview, and how to build, test and run the application.

## Coding guidelines

Use the detailed instructions in @AGENTS.md when writing Akka code.

Use the guidelines in @akka-context/sdk/ai-coding-assistant-guidelines.html.md when writing code in this project.

## Akka documentation

You find the reference documentation of Akka in the akka-context directory and sub-directories.
Read this documentation to answer questions about Akka.

## Generation Workflow

Build the feature from `specs/<feature>/tasks.md`, phase by phase, until every task is done
and `mvn verify` is green. Do not stop for approval between components — the task list is the
plan, and it was reviewed when it was written.

Report per phase, not per file: what was built, what the tests say, what is blocked and why.

### Before writing a component type for the first time in a session

Read the relevant `akka-context/sdk/*.html.md` — mandatory for Workflows, Agents and
Autonomous Agents, whose APIs are easy to get subtly wrong.

### Order within a phase

Domain first (no Akka types, testable with no runtime), then application components, then
endpoints, then integration tests. Tests accompany the code they cover in the same phase.

> Replaces the scaffold's step-by-step approval workflow, which required explicit user
> approval between every component. That gate contradicts `/akka:implement`, which instructs
> the assistant to execute the whole task plan, and it makes a 79-task port impossible to
> deliver. The task list already carries the review the gate was trying to provide.
