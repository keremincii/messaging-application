CC = gcc
CFLAGS = -Wall -Wextra -pthread

# Windows'ta winsock2 için gereklidir
ifeq ($(OS),Windows_NT)
	LDFLAGS = -lws2_32 -lssl -lcrypto
	EXEC = server.exe
else
	# macOS: Homebrew OpenSSL yolu (Apple Silicon + Intel)
	OPENSSL_PREFIX = $(shell brew --prefix openssl 2>/dev/null || echo "/usr/local/opt/openssl")
	CFLAGS += -I$(OPENSSL_PREFIX)/include
	LDFLAGS = -L$(OPENSSL_PREFIX)/lib -lssl -lcrypto
	EXEC = server
endif

SRCS = main.c chat.c crypto.c auth.c
OBJS = $(SRCS:.c=.o)

all: $(EXEC)

$(EXEC): $(OBJS)
	$(CC) $(CFLAGS) -o $@ $^ $(LDFLAGS)

%.o: %.c
	$(CC) $(CFLAGS) -c $< -o $@

clean:
ifeq ($(OS),Windows_NT)
	del /f $(OBJS) $(EXEC)
else
	rm -f $(OBJS) $(EXEC)
endif
