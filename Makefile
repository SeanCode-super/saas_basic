SHELL := /bin/sh

export PATH := $(PATH):/opt/homebrew/bin
.PHONY: backend-build backend-test frontend-install frontend-build frontend-lint frontend-test verify dev-db-up dev-db-down dev-backend dev-frontend

backend-build:
	cd server && ../bin/with-jdk mvn clean package -DskipTests

backend-test:
	cd server && ../bin/with-jdk mvn test

frontend-install:
	cd web && npm ci

frontend-build:
	cd web && npm run build

frontend-lint:
	cd web && npm run lint && npm run lint:style

frontend-test:
	cd web && npm run test:run

verify:
	cd server && ../bin/with-jdk mvn verify
	cd web && npm run check

dev-db-up:
	docker compose up -d mysql

dev-db-down:
	docker compose down

dev-backend:
	cd server && SPRING_CONFIG_ADDITIONAL_LOCATION="optional:file:$(CURDIR)/.env[.properties]" ../bin/with-jdk mvn -pl saas-basics-app spring-boot:run

dev-frontend:
	cd web && npm run dev
