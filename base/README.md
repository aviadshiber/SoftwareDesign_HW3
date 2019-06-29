# CourseApp: Assignment 3

## Authors
* Ron Yitzhak, 315722744
* Aviad Shiber, 302935697

### Previous assignment
This assignment uses the code from the submission by: 315722744-302935697

(This is important for people who switched partners, but everyone needs to fill it out)

## Library selection
This submission uses the library identified by the number: 2

## Notes

### Implementation Summary
-Tree of known bots
-Tree of known channels
-All the features includes a specific tree/s to enable holding details per bot, and details per channel in bot
-Callbacks are hold in locally in CourseBots to enable callback's removal
-All the data structures in memory are trees.
-All objects has an object model in storage to make read/write operations easier

### Testing Summary
We've implemented course app fake to simulate course app behaviour, and using mockk to check some cases.
and

### Difficulties
-Many cases that were not defined in the assignment.
-Many requirements were written only in the pdf and not in the documentation.

### Feedback
Please write the assignment more clearly, including specify exactly what is the meaning of each operation
(for example, bot.part method was'nt defined well).