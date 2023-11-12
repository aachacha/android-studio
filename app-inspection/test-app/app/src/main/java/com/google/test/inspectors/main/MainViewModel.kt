package com.google.test.inspectors.main

import android.app.Application
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.grpc.proto.protoRequest
import com.google.test.inspectors.AppJobService
import com.google.test.inspectors.AppWorker
import com.google.test.inspectors.HttpClient
import com.google.test.inspectors.Logger
import com.google.test.inspectors.grpc.GrpcClient
import com.google.test.inspectors.grpc.json.JsonRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val jobId = AtomicInteger(1)
private const val StopTimeoutMillis: Long = 5000

/**
 * A [SharingStarted] meant to be used with a [kotlinx.coroutines.flow.StateFlow] to expose data to
 * the UI.
 *
 * When the UI stops observing, upstream flows stay active for some time to allow the system to come
 * back from a short-lived configuration change (such as rotations). If the UI stops observing for
 * longer, the cache is kept but the upstream flows are stopped. When the UI comes back, the latest
 * value is replayed and the upstream flows are executed again. This is done to save resources when
 * the app is in the background but let users switch between apps quickly.
 */
private val WhileUiSubscribed: SharingStarted = SharingStarted.WhileSubscribed(StopTimeoutMillis)

@HiltViewModel
internal class MainViewModel
@Inject
constructor(
  private val application: Application,
) : ViewModel(), MainScreenActions {
  private val snackFlow: MutableStateFlow<String?> = MutableStateFlow(null)
  val snackState: StateFlow<String?> = snackFlow.stateIn(viewModelScope, WhileUiSubscribed, null)
  private val grpcClient = GrpcClient()

  override fun startJob() {
    val id = jobId.getAndIncrement()
    val job = JobInfo.Builder(id, ComponentName(application, AppJobService::class.java)).build()
    val jobScheduler = application.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
    jobScheduler.schedule(job)

    snackFlow.value = "Started job $jobId"
  }

  override fun startWork() {
    val request = OneTimeWorkRequest.Builder(AppWorker::class.java).build()
    val workManager = WorkManager.getInstance(application)
    val work: LiveData<WorkInfo> = workManager.getWorkInfoByIdLiveData(request.id)

    viewModelScope.launch {
      work.asFlow().collect {
        Logger.info("State of ${request.id}: ${it.state}")
        if (it.state == WorkInfo.State.SUCCEEDED) {
          snackFlow.value = it.outputData.getString(AppWorker.MESSAGE_KEY)
        }
      }
    }
    workManager.enqueue(request)
  }

  override fun doGet(client: HttpClient, url: String) {
    viewModelScope.launch {
      val result = client.doGet(url)
      snackFlow.value = "${client.name} Result: ${result.rc}"
    }
  }

  override fun doPost(client: HttpClient, url: String, data: ByteArray, type: String) {
    viewModelScope.launch {
      val result = client.doPost(url, data, type)
      snackFlow.value = "${client.name} Result: ${result.rc}"
    }
  }

  override fun doProtoGrpc(name: String) {
    viewModelScope.launch {
      val response = grpcClient.doProtoGrpc(protoRequest { this.name = name })
      snackFlow.value = response.message
    }
  }

  override fun doJsonGrpc(name: String) {
    viewModelScope.launch {
      val response = grpcClient.doJsonGrpc(JsonRequest(name))
      snackFlow.value = response.message
    }
  }
}
