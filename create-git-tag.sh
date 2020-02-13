#!/bin/bash

newTag=$1

git tag $newTag
git push origin $newTag