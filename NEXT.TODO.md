It seems you did not update the readout everywhere. In fact, the one at the top of settings also doesn't reflect this. Once the readout is accurate, make it tell the user each ring's size, e.g: 48KHZ: 0-5m, 16KHZ: 5-15m, 8KHZ: 15-400m.

Then, update the main activity to display the recording groups more dynamically - the silence log should be on the main screen, essentially. The way this should work is it should say at the top how long the current activity zone has been recording for, and then the silence breaks, intercalated with the audio actually present. An example would look like this:

Echo Enabled
------(activity bar/SILENT)------
[CLIP RECORDING]
Activity: 00:09:44 [save from here]
Silence: 02:00:20
Activity: 00:03:20 [save from here]
Activity: 00:05:00 [save from here]
Activity: 00:05:00 [save from here]
Activity: 00:05:00 [save from here]
Silence: 01:20:20
Activity: 00:00:40 [save from here]
Activity: 00:05:00 [save from here]
Activity: 00:05:00 [save from here]
Activity: 00:05:00 [save from here]
Activity: 00:05:00 [save from here]
Activity: 00:05:00 [save from here]
Activity: 00:05:00 [save from here]
---
Memory is limited at 
x minutes
you can change it here
[settings]
---
Echo lets you go back in time and record events from the past.
If you want to know more read FAQ
...


Notice how I'm also adding a "save from here" button? When the user clicks that, all of the buttons should switch to saying "save to here". Now, the user may select the older/newer or from/to in inverse or the correct order. Both should be allowed, merely correct it before finding the audio ranges to pull. If there are no silence segments, then the save button should not be there.

Make sure that only ranges actually within the audio rings are available to select.

See how the max block size is 5 minutes? Let the user choose what these ranges are denominated in (5 minutes, 10 minutes, 15 minutes, 30 minutes, 60 minutes).

If the user selects the same block twice, merely include that entire block. All of these save from/to should be inclusive, meaning selecting 2 blocks should capture [A---B] and not just A[---]B or any other permutation.

Keep the clip recording + dialog as it is today. Merely move that button to the top of the screen, just below the echo enabled button. Additionally, move the "echo lets you go back in time..." text to just above the FAQ text. Please remove the "memory holds the most recent" text entirely.

Instead of "SILENT" and "Live Volume" please make it SILENT vs the activity bar. Basically, the activity bar shouldn't be visible if "SILENT" is and vice versa.

Make sure to create some implementation plans, a TODO list, update that TODO list, commit often, build before each commit and keep working until every feature listed here is implemented.