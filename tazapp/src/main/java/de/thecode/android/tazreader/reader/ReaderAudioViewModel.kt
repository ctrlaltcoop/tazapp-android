package de.thecode.android.tazreader.reader

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.github.ajalt.timberkt.Timber.d
import de.thecode.android.tazreader.TazApplication
import de.thecode.android.tazreader.audio.AudioItem
import de.thecode.android.tazreader.audio.AudioPlayerService

class ReaderAudioViewModel(application: Application) : AndroidViewModel(application) {

    private var service = AudioPlayerService.instance
    private val serviceBroadcastReceiver = ServiceCommunicationReceiver()

    val currentAudioItemLiveData = MutableLiveData<AudioItem?>()
    val isPlayingLiveData = MutableLiveData<Boolean>()

    init {
        syncAudioItemFromService()
        LocalBroadcastManager.getInstance(getApplication())
                .registerReceiver(serviceBroadcastReceiver, IntentFilter(AudioPlayerService.ACTION_SERVICE_COMMUNICATION))
    }

    override fun onCleared() {
        LocalBroadcastManager.getInstance(getApplication())
                .unregisterReceiver(serviceBroadcastReceiver)
    }

    fun startPlaying(audioItem: AudioItem){
        d {
            "start playing $audioItem"
        }
        val intent = Intent(getApplication(), AudioPlayerService::class.java)
        intent.putExtra(AudioPlayerService.EXTRA_AUDIO_ITEM, audioItem)
    
        getApplication<TazApplication>().startService(intent)
    }

    fun stopPlaying() {
        service?.stopPlaying()
    }

    fun pauseOrResume() {
        service?.pauseOrResumePlaying()
    }


    private fun syncAudioItemFromService() {
        service?.let {
            currentAudioItemLiveData.postValue(it.audioItem)
            isPlayingLiveData.postValue(it.isPlaying())
        } ?: run {
            currentAudioItemLiveData.postValue(null)
            isPlayingLiveData.postValue(false)
        }
    }

    inner class ServiceCommunicationReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let{ innerIntent ->
                val message = innerIntent.getStringExtra(AudioPlayerService.EXTRA_COMMUNICATION_MESSAGE)
                message?.let {
                    when(it) {
                        AudioPlayerService.MESSAGE_SERVICE_PREPARE_PLAYING -> {
                            service = AudioPlayerService.instance
                            syncAudioItemFromService()
                        }
                        AudioPlayerService.MESSAGE_SERVICE_DESTROYED -> {
                            service = null
                            syncAudioItemFromService()
                        }
                        AudioPlayerService.MESSAGE_SERVICE_PLAYSTATE_CHANGED -> {
                            syncAudioItemFromService()
                        }
                    }
                }
            }

        }
    }

}