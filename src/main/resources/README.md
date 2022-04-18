Implement simple text based IRC server based on Netty Framework any version. Use Java or Kotlin.

Main logic implementation could be done within 1 source file, with no persistence (in memory only). Please pay extra attention to concurrency and thread safety.

Command set for this server:

/login <name> <password> — if user not exists create profile else login

/join <channel> — try to join channel (max 10 active clients per channel is needed). If client's limit exceeded - send error, otherwise join channel and send last N messages of activity

/leave - disconnect client

/users — show users in the channel

<text message terminated with CR> - sends message to current channel. Server must send new message to all connected to this channel clients.

We should be able to check this server via simple text based telnet command.