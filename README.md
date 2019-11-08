# SoftwareDesign_HW3
This semester we will be developing CourseApp, a “Facebook-killer” that aims to replace Facebook’s (and WhatsApp’s) monopoly on managing course groups. We will be developing the server-side functionality of the program, with the hope that clients will be written as projects


# New Feature- Bots
Adding bots to SoftwareDesign_HW2

## Keyword tracking
The bots can be configured to look for keywords or message types, and count
their occurrences. See the method documentation for more information.
## Tipping
Users give “tips” to each other (but not themselves) by sending a message to
the channel. Each user starts with 1,000 bits in each channel that the bot is in,
and can send some of those to other users in the channel by sending a message
that the bot understands. Each bot keeps a separate ledger.
## Calculator
The bot can do basic math problems for the users. If the trigger is set, then
users can ask the bot to evaluate simple arithmetic expressions with the four
arithmetic operators (+, −, ×, ÷) and parentheses; the normal text characters
are used (+,-,*,/)
## Statistics
The bot keeps track of user activity, such as the last time a user was seen (has
sent a message). Statistics that are per-channel are reset when the bot leaves
## Surveys
The bot can run surveys in channels, asking a question and recording the results
that are seen in the same channel. As usual for surveys, every user can vote
once; voting again just changes the original vote
