.PHONY: build test run docker-build docker-up docker-down

IMAGE_NAME ?= job-scheduler-service
IMAGE_TAG ?= 1.0.0

build:
	mvn clean package -DskipTests

test:
	mvn test

run:
	mvn spring-boot:run

docker-build:
	docker build -t $(IMAGE_NAME):$(IMAGE_TAG) .

docker-up:
	docker compose up -d --build

docker-down:
	docker compose down
