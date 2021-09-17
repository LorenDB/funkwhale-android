#!/bin/bash
# This script creates a new release of Funkwhale for Android.
#
# Usage: ./create-release.sh TAG

# tokenize_tag() - tokenize tag parameter, splitting at dot
#
# Expects a string parameter with format MAJOR.MINOR.PATCH, e.g. 1.2.3
# Returns an array containing elements for MAJOR, MINOR and PATCH
function tokenize_tag() {
  IFS="." read -ra TOKENS <<< "$1"
}

# version_code() - create a numeric version code from a tokenized tag parameter
# that conforms to the version code format from the
# gradle build script
#
# Expects an array parameter with entries for MAJOR, MINOR and PATCH version parts
# Returns a formatted version code, leading zeroes are stripped
function version_code() {

  result=""

  # Every version part is filled with leading zeros to a string of size 2
  for token in "$@"
  do
    ((leadingZeros=2-${#token}))
    for _ in $(seq 1 "$leadingZeros")
    do
      token="${token}0"
    done
    result="$result$token"
  done

  # strip leading zeroes, add three trailing zeroes and return
  echo "${result#"${result%%[!0]*}"}000"
}

# Validate if script is used with correct number of params
if [ $# -lt 1 ]; then
  echo 'Usage: ./create-release.sh TAG' >&2
  echo '  TAG format MAJOR.MINOR.PATH, e.g. 1.2.3' >&2
  exit 1
fi

tokenize_tag "$1"

# Validate tag parameter format
if [ ${#TOKENS[@]} != 3 ]; then
  echo 'ERROR: Invalid tag parameter format.' >&2
  echo 'Use MAJOR.MINOR.PATCH scheme, e.g. 1.2.3' >&2
  exit 1
fi

# Validate that git repository is not dirty
if [ "$(git diff --stat)" != '' ]; then
  echo 'ERROR: repository is dirty.' >&2
  exit 1
fi

TAG="$1"
MESSAGE="Creating release version $TAG"

# Validate that git tag doesn't already exist
git fetch --all --tags
if [ "$(git tag -l | grep -e "^$TAG$")" != '' ]; then
  echo "ERROR: tag $TAG already exists." >&2
  exit 1
fi

echo "Compiling the changelog..."
towncrier build --version "$TAG" --date $(date +"%Y-%m-%d")

git add CHANGELOG
git commit --message "Update changelog for version $TAG"

# Write versionCode and versionName to a file that F-Droid can parse
echo "versionCode = $(version_code "${TOKENS[@]}")
versionName = $TAG" > fdroidversion.txt
git add fdroidversion.txt
git commit --message "Update version information for F-Droid"

# Create and push tag
echo "Tagging the application..."
git tag -a -s -m "$MESSAGE" "$TAG"
git push --tags
