# README #

The ScopeSpeaker Android app can be imported into Android Studio after being cloned from this repository.

### What is this repository for? ###

The ScopeSpeaker app takes a Periscope user name as input and finds that user's current live broadcast (if it exists) and uses the Android text-to-speech library to audibly output each chat message in the broadcast.

Additionally, the chat messages can be translated to the default language of the Android device, if the incoming message packet from the Periscope chat server indicates that the message is not in the default language.
The Yandex translation web service (http://translate.yandex.com) is used to perform the translation.
Yandex supports up to 91 different languages for translation.

It can run in background to output spoken chat messages of a broadcast Periscope session by the user in the foreground, or can be run in split screen mode side-by-side with Periscope so app preferences can be changed while broadcasting (Periscope shuts down the broadcast if another app takes over the screen completely).

The app can also receive 'share' intent messages from Periscope containing the URL of a particularbroadcast, instructing Periscope to say the chat messages of that broadcast.

### Who do I talk to? ###

* John Feras (twitter: @johnferas email: jferas@ferasinfotech.com)

