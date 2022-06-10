package co.daily.core.dailydemo

import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.Patterns
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import co.daily.*
import co.daily.VideoView
import kotlinx.coroutines.launch

private const val TAG = "daily_demo_app"

class MainActivity : AppCompatActivity() {

    private val requestPermissionLauncher =
        registerForActivityResult(RequestMultiplePermissions()) { result ->
            if (result.values.any { !it }) {
                checkPermissions()
            } else {
              // permission is granted, we can initialize
              initialize()
            }
        }

    private lateinit var addurl: EditText
    private lateinit var callCount: TextView
    private lateinit var callClient: CallClient

    // ok, I'm going for it
    private var remoteContainers = arrayOfNulls<FrameLayout?>(3)
    private var remoteVideoViews = arrayOfNulls<VideoView?>(3)
    private var visibleParticipants = arrayOfNulls<Participant?>(3)
    private var participantCount = 0

    private lateinit var joinButton: Button
    private lateinit var leaveButton: Button

//    private lateinit var remoteCameraMaskView: TextView

    private var callClientListener = object : CallClientListener {

        // This demonstrates that the user can ignore individual events
        // override fun onError() {}

        override fun onCallStateUpdated(
            state: CallState
        ) {
            when(state) {
                CallState.joining -> {}
                CallState.joined -> {
                    joinButton.visibility = View.GONE
                    leaveButton.visibility = View.VISIBLE
                    leaveButton.isEnabled = true
                    addurl.visibility = View.GONE
                    // callCount.visibility = View.VISIBLE
                    // This is sus on high participant calls
                    //updateRemoteCameraMaskViewMessage()
                }
                CallState.leaving -> {}
                CallState.left -> {
                    resetAppState()
                }
                CallState.new -> {
                    Log.d(TAG, "CallState.new")
                }
            }
        }

         override fun onParticipantJoined(participant: Participant) {
             // updateRemoteCameraMaskViewMessage()
             if (!participant.info.isLocal && participant.media?.camera?.track != null) {
                 Log.d(TAG, "onParticipantJoined yesVid local ${participant.info.isLocal}")
                 updateParticipantVideoView(participant)
             } else {
                 Log.d(TAG, "onParticipantJoined noVid, local: ${participant.info.isLocal}")

             }
        }

        override fun onParticipantUpdated(participant: Participant) {
            Log.d(TAG, "onParticipantUpdated local ${participant.info.isLocal}")
            // check if participant is in presenting participant list
            updateParticipantVideoView(participant)
        }

        override fun onParticipantLeft(participant: Participant) {
            Log.d(TAG, "onParticipantLeft")
            // Check if it's one of our video on participants
            renderNextParticipantTrack()
            // updateRemoteCameraMaskViewMessage()

        }
    }

    private fun resetAppState() {
        addurl.isEnabled = true
        joinButton.visibility = View.VISIBLE
        joinButton.isEnabled = true
        leaveButton.isEnabled = false
        leaveButton.visibility = View.GONE
        addurl.visibility = View.VISIBLE
        callCount.visibility = View.GONE
        clearRemoteVideoView()
        // remoteCameraMaskView.text = resources.getText(R.string.join_meeting_instructions)
    }

    private fun renderNextParticipantTrack(){
        // Proably don't need this function because
        Log.d(TAG, "renderNextParticipantTrack")
        // Render the next particpant in the list if any
//        val nextParticipant =
//            callClient.participants().all.values.firstOrNull{ !it.info.isLocal && it.media?.camera?.track != null }
//        updateRemoteVideoTrack(nextParticipant?.media?.camera?.track)
    }

    private fun clearRemoteVideoView() {
        for (i in remoteVideoViews.indices) {
            remoteVideoViews[i]?.release()
            remoteContainers[i]?.removeView(remoteVideoViews[i])
            remoteVideoViews[i] = null
            visibleParticipants[i] = null
            // remoteCameraMaskView.visibility = View.VISIBLE
        }
    }

    // This is sus on high participant calls. Need to investigate
    private fun updateRemoteCameraMaskViewMessage() {
        val message =
            when (val amountOfParticipants = callClient.participants().all.size) {
                1 -> resources.getString(R.string.no_one_else_in_meeting)
                2 -> callClient.participants().all.filter { !it.value.info.isLocal }.entries.first().value.info.userName
                else -> resources.getString(R.string.amount_of_participants_at_meeting, amountOfParticipants-1)
            }
        callCount.text = message
    }

    private fun checkDisplaySlots(participant: Participant):Int {
        // ugly check for which slot is open and if the participant is in already
        var openSlot = -1
        var key = -1
        for (i in visibleParticipants.indices) {
            if (visibleParticipants[i] == null && openSlot < 0) {
                openSlot = i
            } else if (visibleParticipants[i]?.id == participant.id) {
                key = i
                break
            }
        }

        if ((key < 0 && openSlot < 0)) {
            key = -1
        } else if (key < 0) {
            key = openSlot
            visibleParticipants[key] = participant
        }
        return key
    }

    private fun updateParticipantVideoView(participant: Participant){
        if (participant.info.isLocal) {
            Log.d(TAG, "Participant: ${participant.id} is local")
        } else {
            Log.d(TAG, "Participant ${participant.id} joined the call!")

            val key = checkDisplaySlots(participant)
            if (key >= 0) {
                updateRemoteVideoTrack(key, participant.media?.camera?.track)
                android.util.Log.d(TAG, "track updated ${participant.media?.camera?.track?.nativeTrack}")
            } else {
                Log.d(TAG, "No free slot for Participant ${participant.id}")
            }
        }
    }

    private fun updateRemoteVideoTrack(key: Int, track: MediaStreamTrack? = null) {
        Log.d(TAG, "updateRemoteVideoTrack ${track}")
        if (remoteVideoViews[key] == null) {
            remoteVideoViews[key] = VideoView(this@MainActivity)
            remoteContainers[key]?.addView(remoteVideoViews[key])
        }
        remoteVideoViews[key]?.track = track
        // hopefully it isn't null at this point
        val containsTrack = track != null
        // remoteCameraMaskView.visibility = if(containsTrack) View.GONE else View.VISIBLE
        remoteVideoViews[key]?.visibility = if(containsTrack) View.VISIBLE else View.GONE

    }

    override fun onCreate(savedInstance: Bundle?) {
        super.onCreate(savedInstance)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        joinButton = findViewById(R.id.call_button)
        leaveButton = findViewById(R.id.hangup_button)

        remoteContainers[0] = findViewById(R.id.remote_video_view_container_1)
        remoteContainers[1] = findViewById(R.id.remote_video_view_container_2)
        remoteContainers[2] = findViewById(R.id.remote_video_view_container_3)

        addurl = findViewById(R.id.aurl)
        // addurl.setText("https://michael.staging.daily.co/playground")
        callCount = findViewById(R.id.participantCountView)
        callCount.visibility = View.GONE

        checkPermissions()
    }

    private fun initialize() {
        initCallClient()
        initEventListeners()
    }

    private fun createClientSettingsIntent():ClientSettingsUpdate {
        return ClientSettingsUpdate(
                inputSettings = InputSettingsUpdate(
                    camera = CameraInputSettingsUpdate(
                        isEnabled = Disable(),
                    ),
                    microphone = MicrophoneInputSettingsUpdate(
                        isEnabled = Disable(),
                    )
                )
        )
    }

    private fun showMessage(message: String) {
        val layout = layoutInflater.inflate(R.layout.custom_toast, findViewById(R.id.custom_toast_id))
        val toastMessage:TextView = layout.findViewById(R.id.custom_toast_message)
        toastMessage.text = message
        val toast = Toast.makeText(applicationContext, message, Toast.LENGTH_LONG)
        toast.view = layout
        toast.setGravity(Gravity.TOP, 0, 20)
        toast.show()
    }

    private fun initEventListeners() {
        addurl.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val containsUrl = Patterns.WEB_URL.matcher(addurl.text.toString()).matches()
                joinButton.isEnabled = containsUrl
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        val clientSettingsIntent = createClientSettingsIntent()
        joinButton.setOnClickListener {
            // Only enable leave after joined
            joinButton.isEnabled = false
            addurl.isEnabled = false
            lifecycleScope.launch {
                try {
                    val url = addurl.text.toString()
                    callClient.join(url, clientSettingsIntent)
                    Log.d(TAG, "ClientSettings ${clientSettingsIntent}")
                    val participants = callClient.participants()
                    Log.d(TAG, "participants $participants")
                    Log.d(TAG, "me $participants.local")
                    Log.d(TAG, "all ${callClient.participants().all}")

                    Log.d(TAG, "inputs ${callClient.inputs()}")
                    Log.d(TAG, "publishing ${callClient.publishing()}")
                    Log.d(TAG, "call state ${callClient.callState()}")
                } catch (e: UnknownCallClientError) {
                    Log.d(TAG, "Failed to join call")
                    callClient?.leave()
                    showMessage("Failed to join call")
                }
            }
        }

        leaveButton.setOnClickListener {
            it.isEnabled = false
            callClient.leave()
        }
    }

    private fun initCallClient() {
        callClient = CallClient(applicationContext).apply {
            addListener(callClientListener)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        callClient.onDestroy()
    }

    private fun checkPermissions() {
        val permissionList = applicationContext.packageManager.
            getPackageInfo(applicationContext.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions

        val notGrantedPermissions = permissionList.map {
            Pair(it, ContextCompat.checkSelfPermission(applicationContext, it))
        }.filter {
            it.second != PackageManager.PERMISSION_GRANTED
        }.map {
            it.first
        }.toTypedArray()

        if (notGrantedPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(notGrantedPermissions)
        } else {
            // permission is granted, we can initialize
            initialize()
        }
    }
}
