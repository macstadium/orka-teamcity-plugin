#!/bin/bash

set -eo pipefail

newTag=$1

git tag $newTag
git push origin $newTag
