SHELL := /bin/bash

# The coworker protocol (MojoKata/coworker): every make run remembers SeedKata on this machine and
# says what it lacks of .coworker/deps.json (~/coworker/bin/deps check . lists it); it stops if
# coworker isn't installed.
COWORKER_HOME ?= $(HOME)/coworker
ifeq ($(wildcard $(COWORKER_HOME)/bin/deps),)
$(error coworker isn't installed in $(COWORKER_HOME): run install.sh from a checkout of MojoKata/coworker)
endif
ifneq ($(shell "$(COWORKER_HOME)/bin/deps" register "$(CURDIR)" >&2 || echo failed),)
$(error coworker: deps register failed (above))
endif

FOUNDATION_SDK_ROOT ?= $(HOME)/.foundation/sdk/current
ANDROID_HOME ?= $(HOME)/android-sdk
JAVA_HOME ?= $(HOME)/.nix-profile/lib/openjdk
export JAVA_HOME

.PHONY: all prime droid zip clean

all: prime droid zip

# Foundation Prime app: build + sign + pack, then stage the release
prime:
	cd prime && FOUNDATION_SDK_ROOT=$(FOUNDATION_SDK_ROOT) \
		nix develop $(FOUNDATION_SDK_ROOT) --command sh -c 'foundation pack --release'
	mkdir -p releases/prime
	cp prime/target/keyos/diceseed.app releases/prime/diceseed.app
	cd releases/prime && sha256sum diceseed.app > diceseed.app.sha256
	@echo "== Prime release =="
	@cat releases/prime/diceseed.app.sha256

# Android app: assemble the signed release APK, then stage the release
droid:
	cd droid && ANDROID_HOME=$(ANDROID_HOME) ./gradlew --no-daemon assembleRelease
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
	cd prime && FOUNDATION_SDK_ROOT=$(FOUNDATION_SDK_ROOT) \
		nix develop $(FOUNDATION_SDK_ROOT) --command sh -c 'foundation clean' || true
	cd droid && ./gradlew --no-daemon clean || true
	rm -rf releases/prime/* releases/droid/*
