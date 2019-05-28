 #!/usr/bin/env python
# -*- coding: utf8 -*-

import RPi.GPIO as GPIO
from MFRC522 import MFRC522
import signal

continue_reading = True


# Capture SIGINT for cleanup when the script is aborted
def end_read(signal, frame):
    global continue_reading
    print("Ctrl+C captured, ending read.")
    continue_reading = False
    GPIO.cleanup()

# Hook the SIGINT
signal.signal(signal.SIGINT, end_read)

# Create an object of the class MFRC522
# Correct values for the Raspberry Pi
MIFAREReader = MFRC522.Reader(0, 0, 22)

# This loop keeps checking for chips. If one is near it will get the UID and authenticate
while continue_reading:
    # Scan for cards
    (status, TagType) = MIFAREReader.MFRC522_Request(MIFAREReader.PICC_REQIDL)

    # If a card is found
    if status == MIFAREReader.MI_OK:
        print("Card detected")

    # Get the UID of the card
    (status, uid) = MIFAREReader.MFRC522_Anticoll()

    # If we have the UID, continue
    if status == MIFAREReader.MI_OK:

        # Print UID
        print("Card read UID: %d, %d, %d, %d, %d" % tuple(uid))

        # This is the default key for authentication
        key = [0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF]

        # Select the scanned tag
        MIFAREReader.MFRC522_SelectTag(uid)

        # Authenticate
        status = MIFAREReader.MFRC522_Auth(MIFAREReader.PICC_AUTHENT1A, 4, key, uid)
        print("\n")

        # Check if authenticated
        if status == MIFAREReader.MI_OK:

            # Variable for the data to write
            data = []

            # Fill the data with 0xFF
            for x in range(0, 16):
                data.append(0xFF)
            
            print(data)
            print("Sector 4 looked like this:")
            # Read block 4
            addr, card_data = MIFAREReader.MFRC522_Read(4)
            print("Sector %d:\n%s" % (addr, str(card_data)))
            print("\n")

            print("Sector 4 will now be filled with 0xFF:")
            # Write the data
            MIFAREReader.MFRC522_Write(4, data)
            print("\n")

            print("It now looks like this:")
            # Check to see if it was written
            addr, card_data = MIFAREReader.MFRC522_Read(4)
            print("Sector %d:\n%s" % (addr, str(card_data)))
            print("\n")

            data = []
            # Fill the data with 0x00
            for x in range(0, 16):
                data.append(0x00)

            print("Now we fill it with 0x00:")
            MIFAREReader.MFRC522_Write(4, data)
            print("\n")

            print("It is now empty:")
            # Check to see if it was written
            addr, card_data = MIFAREReader.MFRC522_Read(4)
            print("Sector %d:\n%s" % (addr, str(card_data)))
            print("\n")

            # Stop
            MIFAREReader.MFRC522_StopCrypto1()

            # Make sure to stop reading for cards
            continue_reading = False
        else:
            print("Authentication error")

GPIO.cleanup()
