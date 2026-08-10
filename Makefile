SHELL := /bin/sh

.PHONY: backend-build backend-test frontend-install frontend-build frontend-lint frontend-test verify dev-db-up dev-db-down dev-backend dev-frontend

backend-build:
	cd server && mvn clean package -DskipTests

backend-test:
	cd server && mvn test

frontend-install:
	cd web && npm ci

frontend-build:
	cd web && npm run build

frontend-lint:
	cd web && npm run lint && npm run lint:style

frontend-test:
	cd web && npm run test:run

verify:
	cd server && mvn verify
	cd web && npm run check

dev-db-up:
	docker compose up -d mysql

dev-db-down:
	docker compose down

dev-backend:
	cd server && DB_HOST=127.0.0.1 DB_PORT=$${DB_PORT:-3307} DB_NAME=saas_basics DB_USERNAME=root DB_PASSWORD=root mvn -pl saas-basics-app spring-boot:run

dev-frontend:
	cd web && npm run dev
