# IRCServer
Simple IRC server for telnet client (but not necesserely) based on Netty

Command set for this server:

/login <name> <password> — if user not exists create profile else login

/join <channel> — try to join channel (max 10 active clients per channel is needed). If client's limit exceeded - send error, otherwise join channel and send last N messages of activity

/leave - disconnect client

/users — show users in the channel

/channels - show list of available channels
  
<text message terminated with CR> - sends message to current channel. Server attemptsto send new message to all connected to this channel clients.
  
By default, after performing mvn package the ircd-full.jar file will be generated. In order to start the app just hit java -jar ircd-full.jar
