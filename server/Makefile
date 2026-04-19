CC = gcc
CFLAGS = -Wall -Wextra -pthread

# Windows'ta winsock2 için gereklidir
ifeq ($(OS),Windows_NT)
	LDFLAGS = -lws2_32
	EXEC = server.exe
else
	LDFLAGS = 
	EXEC = server
endif

SRCS = main.c auth.c chat.c
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
