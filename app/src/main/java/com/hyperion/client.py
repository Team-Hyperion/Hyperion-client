import binascii
import socket
import sys
import time

def PrintHelp():
    print("Usage: <ip> <P/V> <Path>")
    print("P = Picture")
    print("V = Video")

def main():
    if len(sys.argv) != 5:
        print("Invalid arguments")
        PrintHelp()
        return 1

    server_ip = sys.argv[1]
    
    server_port = sys.argv[2]

    media_type = b"\x00"
    if sys.argv[3] == "P":
        media_type = b"\x00"
    elif sys.argv[3] == "V":
        media_type = b"\x01"
    else:
        print("Invalid media type")
        PrintHelp()
        return 2

    media_path = sys.argv[4]


    sock = socket.socket()
    sock.connect((server_ip, int(server_port)))

    # 1920 = \x07\x80
    # 1080 = \x04\x38

    # 1280 = \x05\x00
    # 720  = \x02\xD0

    # 60 = 3C

    ######## Greeting
    sock.sendall(media_type)  #\x00 for image \x01 for video / mp3

    sock.sendall(b"\x07\x80") # Width # Leave these
    sock.sendall(b"\x04\x38") # Height
    sock.sendall(b"\x01")     # FPS
    ########


    with open(media_path, 'rb') as f:
        for chunk in iter(lambda: f.read(32), b''):
            #s = str(binascii.hexlify(chunk))
            #print(' '.join(a+b for a,b in zip(s[::2], s[1::2])))
            sock.sendall(chunk)


    time.sleep(3) # Wait for server to process, in the future the server will disconnect the client

    sock.shutdown(socket.SHUT_WR)
    sock.close()

    return 0

sys.exit(main())
