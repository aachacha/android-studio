#!/bin/bash -x
#
# Build and test a set of targets via bazel using RBE.

# http://g3doc/wireless/android/build_tools/g3doc/public/buildbot#environment-variables
readonly BUILD_NUMBER="${BUILD_NUMBER:-SNAPSHOT}"
# AS_BUILD_NUMBER is the same as BUILD_NUMBER but omits the P for presubmit,
# to satisfy Integer.parseInt in BuildNumber.parseBuildNumber
# The "#P" matches "P" only at the beginning of BUILD_NUMBER
readonly AS_BUILD_NUMBER="${BUILD_NUMBER/#P/0}"

if [[ $BUILD_NUMBER == "SNAPSHOT" ]];
then
  readonly BUILD_TYPE="LOCAL"
elif [[ $BUILD_NUMBER =~ ^P[0-9]+$ ]];
then
  readonly BUILD_TYPE="PRESUBMIT"
else
  readonly BUILD_TYPE="POSTSUBMIT"
fi

readonly SCRIPT_DIR="$(dirname "$0")"
readonly SCRIPT_NAME="$(basename "$0")"

readonly CONFIG_OPTIONS="--config=ci"

####################################
# Generates flag values and runs bazel test.
# Globals:
#   AS_BUILD_NUMBER
#   BUILD_NUMBER
#   BUILD_TYPE
#   CONFIG_OPTIONS
#   DIST_DIR
#   SCRIPT_DIR
#   SCRIPT_NAME
# Arguments:
#   None
####################################
function run_bazel_test() {
  if [[ $BUILD_TYPE == "LOCAL" ]];
  then
    # Assuming manual user invocation and using limited host resources.
    # This should prevent bazel from causing the host to freeze due to using
    # too much memory.
    local -r worker_instances=2
  else
    local -r worker_instances=auto
  fi

  local build_tag_filters=-no_linux
  # Skip all explicitly generated K2 tests, because all tests will be run with
  # the K2 flag regardless, making the generated K2 test targets redundant with
  # their ordinarily-non-K2 counterparts.
  local test_tag_filters=-no_linux,-no_test_linux,-no_k2,-qa_smoke,-qa_fast,-qa_unreliable,-perfgate,-very_flaky,-kotlin-plugin-k2
  local target_name="studio-linux-k2"

  # provide BES keyword(s) identifying run features (in this instance, the K2 compiler)
  local -r bes_features="k2"

  declare -a conditional_flags
  if [[ $BUILD_TYPE == "POSTSUBMIT" ]]; then
    conditional_flags+=(--bes_keywords=ab-postsubmit)
    conditional_flags+=(--nocache_test_results)
    conditional_flags+=(--flaky_test_attempts=2)
  fi

  # Generate a UUID for use as the bazel test invocation id
  local -r invocation_id="$(uuidgen)"

  if [[ -d "${DIST_DIR}" ]]; then
    # Generate a simple html page that redirects to the test results page.
    echo "<head><meta http-equiv=\"refresh\" content=\"0; URL='https://fusion2.corp.google.com/invocations/${invocation_id}'\" /></head>" > "${DIST_DIR}"/upsalite_test_results.html
  fi

  # Workaround: This invocation [ab]uses --runs_per_test to disable caching for the
  # iml_to_build_consistency_test see https://github.com/bazelbuild/bazel/issues/6038
  # This has the side effect of running it twice, but as it only takes a few seconds that seems ok.
  local -r extra_test_flags=(--runs_per_test=//tools/base/bazel:iml_to_build_consistency_test@2)

  # Run Bazel
  "${SCRIPT_DIR}/bazel" \
    --max_idle_secs=60 \
    test \
    ${CONFIG_OPTIONS} --config=ants \
    --jvmopt='-Didea.kotlin.plugin.use.k2=true -Dlint.use.fir.uast=true -Didea.kotlin.plugin.plugin.ids.to.ignore.k2.compatibility=com.google.tools.ij.aiplugin' \
    --worker_max_instances=${worker_instances} \
    --invocation_id=${invocation_id} \
    --build_tag_filters=${build_tag_filters} \
    --build_event_binary_file="${DIST_DIR:-/tmp}/bazel-${BUILD_NUMBER}.bes" \
    --define=meta_android_build_number="${BUILD_NUMBER}" \
    --build_metadata=ANDROID_BUILD_ID="${BUILD_NUMBER}" \
    --build_metadata=ANDROID_TEST_INVESTIGATE="http://ab/tests/bazel/${invocation_id}" \
    --build_metadata=ab_build_id="${BUILD_NUMBER}" \
    --build_metadata=ab_target="${target_name}" \
    --test_tag_filters=${test_tag_filters} \
    --experimental_execution_graph_log="${TMPDIR:-/tmp}/execution_graph_dump.proto.zst" \
    --experimental_execution_graph_log_dep_type=all \
    --tool_tag=${SCRIPT_NAME} \
    --embed_label="${AS_BUILD_NUMBER}" \
    --profile="${DIST_DIR:-/tmp}/profile-${BUILD_NUMBER}.json.gz" \
    --bes_keywords="$bes_features" \
    "${extra_test_flags[@]}" \
    "${conditional_flags[@]}" \
    -- \
    $(< "${SCRIPT_DIR}/targets")
}

####################################
# Copies bazel worker logs to an output directory 'bazel_logs'.
# Globals:
#   BUILD_NUMBER
#   DIST_DIR
#   SCRIPT_DIR
####################################
function copy_bazel_worker_logs() {
  local -r output_base="$(${SCRIPT_DIR}/bazel info output_base)"
  local -r worker_log_dir="${DIST_DIR:-/tmp/${BUILD_NUMBER}/studio_linux}/bazel_logs"
  mkdir -p "${worker_log_dir}"
  cp "${output_base}/bazel-workers/*.log" "${worker_log_dir}"
}

run_bazel_test
readonly BAZEL_STATUS=$?

# Save bazel worker logs.
# Common bazel codes fall into the single digit range. If a less common exit
# code happens, then we copy extra bazel logs.
if [[ $BAZEL_STATUS -gt 9 ]]; then
  copy_bazel_worker_logs
fi
