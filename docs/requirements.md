# DiceSeed Requirements

DiceSeed is a BIP39 seed generator for Passport Prime (KeyOS) that derives seed
entropy exclusively from physical dice rolls entered by the user.

## 1. Screens

### 1.1. Screen 1: Launch screen
Start with the stock template welcome screen (see `sim-shot1`).

- 1.1.1. Change the banner image to have two 6-sided and two 8-sided dice.
- 1.1.2. Use a steampunk theme for the dice material and surface.
- 1.1.3. Render the dice as 3D objects with one of the 8-sided dice "closest"
  to the screen and the other dice further away.
- 1.1.4. Render the dice scene as if the dice were rolling towards the screen.

### 1.2. Screen 2: Word selection
- 1.2.1. Reached by the "Get Started" button from the Launch screen.
- 1.2.2. Present two buttons: "12 words" and "24 words".
- 1.2.3. Explain the difference between the two word sets succinctly above the
  buttons.

### 1.3. Screen 3: Dice selection
- 1.3.1. Reached by selecting either of the "12/24 words" buttons from
  Screen 2.
- 1.3.2. Present two buttons: "6-sided" and "8-sided".
- 1.3.3. Show pictures (matching the style of the launch screen) of the two
  types of dice above the buttons.

### 1.4. Screen 4: Dice Quality
- 1.4.1. It asks the user to select the dice quality as follows:

  ```
  How fair are your dice?
  1. I trust them (1x)
  2. Maybe biased (~1.5x)
  3. Very sus (2x)
  4. Maybe loaded (~4-7x)
  ```

- 1.4.2. The words should explain that biased dice can be compensated for
  with additional dice rolls.
  - 1.4.2.1. The more paranoid approaches show the approximate number of
    additional dice rolls.
- 1.4.3. Option 1 uses straight bit-mapping of the dice rolls.
- 1.4.4. Option 2 uses hash conditioning: rolls are collected with a ~1.5x
  entropy margin and the seed entropy is the SHA-256 digest of all rolls.
- 1.4.5. Option 3 uses the modular sum of 2 dice rolls: each pair of rolls is
  combined as (a + b) mod faces before bit-mapping.
- 1.4.6. Option 4 uses pairwise-comparison (generalized von Neumann)
  extraction: rolls are compared in pairs, ties are discarded.

### 1.5. Screen 5: Dice Rolling screen (see section 2)

### 1.6. Screen 6: Words screen (see section 3)

### 1.7. Screen 7: Verify screen (see section 4)

### 1.8. Navigation
All screens past the first can navigate backwards to the previous screen.

## 2. Dice Rolling

- 2.1. Directly above the per-word progress bar, a progress bar shows the
  overall entropy collected, labeled "Bits: 0 of 128", counting up as rolls
  are entered.
  - 2.1.1. The bit target reflects the session: 128 bits for 12 words, 256
    bits for 24 words; in hash-conditioning mode the target includes its
    ~1.5x collection margin.
- 2.2. Each word (starting with "1") is presented first as a progress bar.
- 2.3. At the bottom of the screen are buttons for each face of the dice.
  - 2.3.1. For 6-sided mode: 1, 2, 3, 4, 5, 6 — in two rows of three
    buttons (3x2).
  - 2.3.2. For 8-sided mode: 1, 2, 3, 4, 5, 6, 7, 8 — in two rows of four
    buttons (4x2).
  - 2.3.3. An image of the selected die type (matching the launch screen
    style) is shown to the left of the face buttons.
- 2.4. The number of dice rolls so far and the estimated number of rolls
  remaining are shown above the buttons.
  - 2.4.1. For example: "Roll 1 of 84 (estimated)", then
    "Roll 2 of 84 (estimated)", etc.
- 2.5. User presses a button to roll as they roll dice.
- 2.6. As each dice roll is input, the progress bars are updated.
  - 2.6.1. When enough data is available to produce a word:
    - 2.6.1.1. The word's progress bar is replaced by the word.
    - 2.6.1.2. The next word is presented with its progress bar.
- 2.7. A left arrow button rolls back the last dice roll. This is not the
  same as the back button.
  - 2.7.1. The button remains available after the final roll (alongside
    "Continue"), so the last roll can still be undone; undoing it returns
    the screen to the rolling state.
- 2.8. Backwards navigation uses the device's physical back button (or the
  system back gesture). If it is pressed after any dice rolls have been
  entered, a popup asks "Cancel dice rolling?".
  - 2.8.1. If confirmed, the app navigates back to the previous screen and
    discards all dice rolls.
  - 2.8.2. If declined, the user stays on the dice rolling screen with all
    rolls intact.
  - 2.8.3. A back gesture while the popup is shown dismisses the popup; only
    the Discard button abandons the dice rolls.
- 2.9. Once the last dice is rolled:
  - 2.9.1. Calculate the checksum for the last seed word. Use the dice for the
    non-checksum bits of the last word.
  - 2.9.2. Replace dice roll buttons with "Continue" button and "Dice rolls
    complete" message.
  - 2.9.3. Continue button will take the user to the Words screen.

## 3. Words screen

- 3.1. Present the 12 or 24 words in two columns, 12 words per page.
- 3.2. For 24-word seeds, back/forward buttons navigate between the two pages
  of words.
- 3.3. Above the two bottom buttons, display the message "Be sure to write
  down your seed words".
- 3.4. Two buttons at the bottom: "Trust me!" and "Verify".
  - 3.4.1. "Trust me!" button will take the user back to the Launch screen.
  - 3.4.2. Verify button will take the user to the Verify screen.

## 4. Verify screen

- 4.1. Ask the user to verify all the words in the list in random order.
- 4.2. Present 2 columns of 3 candidate words, with the correct word in a
  random location.
- 4.3. If the user presses the correct word, advance to the next word.
- 4.4. If they press the wrong word, change the color of the button they
  pressed to red.
- 4.5. Once all words have been verified, add a "Done" button to take the user
  back to the Launch screen.
