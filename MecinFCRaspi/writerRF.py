import sys
import json
import RPi.GPIO as GPIO
import MFRC522
import signal
import base64

continue_reading = True

data = json.loads(base64.b64decode(sys.argv[1]).decode("utf-8"))
print(data['0'])

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

        key = [0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF]
        # Select the scanned tag
        MIFAREReader.MFRC522_SelectTag(uid)

        status = MIFAREReader.MFRC522_Auth(MIFAREReader.PICC_AUTHENT1A, 0, key, uid)

        # Variable for the data to write
        sectorData = data['0'].split()
        print(int("0x" + sectorData[0],16))
        blockData =[]
        for x in sectorData:
            blockData.append(int("0x" + x,16))
        
        print(str(blockData))
        MIFAREReader.MFRC522_Write(0, blockData)
        continue_reading = False

##        print("Sector 4 will now be filled with 0xFF:")
##        # Write the data
##        MIFAREReader.MFRC522_Write(0, sectorData)
##        print("\n")
##
##        print("It now looks like this:")
##        # Check to see if it was written
##        addr, card_data = MIFAREReader.MFRC522_Read(0)
##        print("Sector %d:\n%s" % (addr, str(card_data)))
##        print("\n")
##
##
##        # Stop
##        MIFAREReader.MFRC522_StopCrypto1()
##
##        # Make sure to stop reading for cards
		
    else:
        print("Authentication error")

GPIO.cleanup()
