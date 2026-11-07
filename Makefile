SHELL := /bin/bash

# The coworker protocol (the coworker/ submodule, MojoKata/coworker): every make run remembers
# SeedKata on this machine and says what it lacks of .coworker/deps.json (coworker/bin/coworker
# status lists it). The submodule checks the installed coworker: it stops the run on a protocol
# that doesn't match (exit 3, with what to run), and only says so when none is installed.
ifneq ($(shell "$(CURDIR)/coworker/bin/deps" register "$(CURDIR)" >&2 || echo failed),)
$(error coworker: deps register failed (above))
endif

FOUNDATION_SDK_ROOT ?= $(HOME)/.foundation/sdk/current
ANDROID_HOME ?= $(HOME)/android-sdk

# Both apps build in containers, so the host needs Docker, not Nix or a JDK. Each image is pinned
# by its digest; the Nix store and the Gradle cache live in named volumes, so a rebuild is fast.
# DOCKER_ARGS adds docker run arguments (an extra mount, say).
NIX_IMAGE := nixos/nix:2.35.4@sha256:f159a58f390677f41e8e900a74b6e2f9c7f27b364122ae6acf7a652ce6c551c0
JDK_IMAGE := eclipse-temurin:17-jdk@sha256:43338b737d0f75d6883d3c4563bc66974113ca8ac296e82f39d161fb3d398f0d
DOCKER_MEMORY ?= 6g
DOCKER_ARGS ?=
USER_IDS := $(shell id -u):$(shell id -g)

.PHONY: all prime droid zip clean

all: prime droid zip

# Foundation Prime app: build + sign + pack, then stage the release
# The SDK's Nix shell in nixos/nix, as root (the store is root's), with ~/.foundation (the SDK and
# the publisher signing identity) mounted read-only at its own path; prime/ goes back to the user.
prime:
	docker run --rm --memory $(DOCKER_MEMORY) $(DOCKER_ARGS) \
		-v seedkata-nix:/nix -v seedkata-home:/root \
		-v "$(HOME)/.foundation:$(HOME)/.foundation:ro" -v "$(CURDIR):/src" -w /src/prime \
		-e FOUNDATION_SDK_ROOT=$(FOUNDATION_SDK_ROOT) -e NIX_CONFIG="experimental-features = nix-command flakes" \
		$(NIX_IMAGE) sh -c 'ln -sfn $(HOME)/.foundation /root/.foundation; \
			nix develop $(FOUNDATION_SDK_ROOT) --command sh -c "foundation pack --release"; rc=$$?; \
			chown -R $(USER_IDS) /src/prime; exit $$rc'
	mkdir -p releases/prime
	cp prime/target/keyos/diceseed.app releases/prime/diceseed.app
	cd releases/prime && sha256sum diceseed.app > diceseed.app.sha256
	@echo "== Prime release =="
	@cat releases/prime/diceseed.app.sha256

# Android app: assemble the signed release APK, then stage the release
# Gradle in a JDK 17 image as the user, with the Android SDK read-only at its own path and Gradle's
# cache in a volume (made the user's first). droid/keystore.properties signs the release.
droid:
	docker run --rm -v seedkata-gradle:/gradle $(JDK_IMAGE) chown $(USER_IDS) /gradle
	docker run --rm --memory $(DOCKER_MEMORY) --user $(USER_IDS) $(DOCKER_ARGS) \
		-v seedkata-gradle:/gradle -e GRADLE_USER_HOME=/gradle -e HOME=/tmp \
		-v "$(ANDROID_HOME):$(ANDROID_HOME):ro" -e ANDROID_HOME=$(ANDROID_HOME) \
		-v "$(CURDIR):/src" -w /src/droid \
		$(JDK_IMAGE) ./gradlew --no-daemon assembleRelease
	mkdir -p releases/droid
	cp droid/app/build/outputs/apk/release/app-release.apk releases/droid/diceseed.apk
	cd releases/droid && sha256sum diceseed.apk > diceseed.apk.sha256
	@echo "== Android release =="
	@cat releases/droid/diceseed.apk.sha256

# Source archive: everything in the repo not excluded by .gitignore
zip:
	mkdir -p releases
	rm -f releases/source.zip
	@sed -e 's/\#.*//' -e '/^[[:space:]]*$$/d' -e 's|^/||' -e 's|/$$||' .gitignore \
		| while read -r p; do echo "$$p"; echo "$$p/*"; done > releases/.zip-excludes; \
	zip -rq releases/source.zip . -x@releases/.zip-excludes; \
	rm -f releases/.zip-excludes; \
	echo "== Source archive =="; \
	unzip -l releases/source.zip | tail -1

clean:
	rm -rf prime/target prime/.foundation-sdk droid/build droid/app/build droid/.gradle
	rm -rf releases/prime/* releases/droid/*
