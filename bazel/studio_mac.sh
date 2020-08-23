#!/bin/bash -x
# Invoked by Android Build Launchcontrol for continuous builds.

# Expected arguments:
readonly out_dir="$1"
readonly dist_dir="$2"
readonly build_number="$3"

if [[ $build_number =~ ^[0-9]+$ ]];
then
  IS_POST_SUBMIT=true
fi

readonly script_dir="$(dirname "$0")"
readonly script_name="$(basename "$0")"

# Invocation ID must be lower case in Upsalite URL
readonly invocation_id=$(uuidgen | tr A-F a-f)

readonly config_options="--config=local --config=release --config=cloud_resultstore"

"${script_dir}/bazel" \
        --max_idle_secs=60 \
        test \
        --keep_going \
        ${config_options} \
        --invocation_id=${invocation_id} \
        --build_tag_filters=-no_mac \
        --build_event_binary_file="${dist_dir}/bazel-${build_number}.bes" \
        --test_tag_filters=-no_mac,-no_test_mac,-qa_sanity,-qa_fast,-qa_unreliable,-perfgate \
        --tool_tag=${script_name} \
        --define=meta_android_build_number=${build_number} \
        --profile=${dist_dir}/mac-profile-${build_number}.json.gz \
        -- \
        //tools/... \
        //tools/base/profiler/native/trace_processor_daemon \
        //tools/vendor/adt_infra_internal/rbe/logscollector:logs-collector_deploy.jar \
        -//tools/base/build-system/integration-test/... \
        -//tools/adt/idea/android-lang:intellij.android.lang.tests_tests \
        -//tools/adt/idea/profilers-ui:intellij.android.profilers.ui_tests \
        -//tools/base/build-system/builder:tests.test

readonly bazel_status=$?

if [[ -d "${dist_dir}" ]]; then
  # info breaks if we pass --config=local or --config=cloud_resultstore because they don't
  # affect info, so we need to pass only --config=release here in order to fetch the proper
  # binaries
  readonly bin_dir="$("${script_dir}"/bazel info --config=release bazel-bin)"
  cp -a ${bin_dir}/tools/base/dynamic-layout-inspector/skiaparser.zip ${dist_dir}
  cp -a ${bin_dir}/tools/base/profiler/native/trace_processor_daemon/trace_processor_daemon ${dist_dir}
  echo "<meta http-equiv=\"refresh\" content=\"0; URL='https://source.cloud.google.com/results/invocations/${invocation_id}'\" />" > "${dist_dir}"/upsalite_test_results.html

  readonly java="prebuilts/studio/jdk/mac/Contents/Home/bin/java"
  ${java} -jar "${bin_dir}/tools/vendor/adt_infra_internal/rbe/logscollector/logs-collector_deploy.jar" \
    -bes "${dist_dir}/bazel-${build_number}.bes" \
    -testlogs "${dist_dir}/logs/junit"
fi

BAZEL_EXITCODE_TEST_FAILURES=3

# For post-submit builds, if the tests fail we still want to report success
# otherwise ATP will think the build failed and there are no tests. b/152755167
if [[ $IS_POST_SUBMIT && $bazel_status == $BAZEL_EXITCODE_TEST_FAILURES ]]; then
  exit 0
else
  exit $bazel_status
fi
