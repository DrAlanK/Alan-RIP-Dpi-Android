#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <errno.h>
#include <fcntl.h>
#include <time.h>
#include <pthread.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <arpa/inet.h>
#include <netdb.h>
#include <poll.h>

static volatile int g_ent_running = 0;
static volatile int g_ent_sock_fd = -1;
static pthread_t g_ent_thread;
static char g_ent_host[256] = {0};
static int g_ent_port = 0;
static int g_ent_proto = 0; // 1 = IKEv2, 2 = L2TP, 3 = AnyConnect

static long long now_ms(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (long long)ts.tv_sec * 1000LL + (ts.tv_nsec / 1000000LL);
}

static int resolve_ipv4(const char* host, struct in_addr* out_addr) {
    if (!host || !host[0]) return -1;
    if (inet_pton(AF_INET, host, out_addr) == 1) {
        return 0;
    }
    struct addrinfo hints, *res = NULL;
    memset(&hints, 0, sizeof(hints));
    hints.ai_family = AF_INET;
    if (getaddrinfo(host, NULL, &hints, &res) != 0 || !res) {
        return -1;
    }
    struct sockaddr_in* sin = (struct sockaddr_in*)res->ai_addr;
    *out_addr = sin->sin_addr;
    freeaddrinfo(res);
    return 0;
}

static void build_raw_ikev2_init(unsigned char* buf, int* out_len, int nat_t) {
    int offset = 0;
    if (nat_t) {
        memset(buf, 0, 4);
        offset = 4;
    }
    unsigned char hdr[48] = {
        0x41, 0x4C, 0x41, 0x4E, 0x16, 0x00, 0x99, 0x01,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x21, 0x20, 0x22, 0x08, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x30,
        0x00, 0x00, 0x00, 0x14, 0x00, 0x00, 0x00, 0x10,
        0x01, 0x01, 0x00, 0x01, 0x03, 0x00, 0x00, 0x08,
        0x01, 0x00, 0x00, 0x0C
    };
    unsigned int r = (unsigned int)now_ms();
    hdr[4] = (unsigned char)(r & 0xFF);
    hdr[5] = (unsigned char)((r >> 8) & 0xFF);
    memcpy(buf + offset, hdr, 48);
    *out_len = offset + 48;
}

static void build_raw_l2tp_sccrq(unsigned char* buf, int* out_len) {
    unsigned char pkt[20] = {
        0xC8, 0x02, 0x00, 0x14, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x80, 0x08, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x02
    };
    memcpy(buf, pkt, 20);
    *out_len = 20;
}

static void* enterprise_raw_worker(void* arg) {
    (void)arg;
    struct in_addr addr;
    if (resolve_ipv4(g_ent_host, &addr) != 0) {
        g_ent_running = 0;
        return NULL;
    }

    if (g_ent_proto == 1 || g_ent_proto == 2) {
        // Raw UDP session (IKEv2 / L2TP) in C core
        int fd = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
        if (fd < 0) {
            g_ent_running = 0;
            return NULL;
        }
        g_ent_sock_fd = fd;
        struct sockaddr_in dst;
        memset(&dst, 0, sizeof(dst));
        dst.sin_family = AF_INET;
        dst.sin_port = htons((uint16_t)g_ent_port);
        dst.sin_addr = addr;

        connect(fd, (struct sockaddr*)&dst, sizeof(dst));

        unsigned char pkt[64];
        int pkt_len = 0;
        if (g_ent_proto == 1) {
            build_raw_ikev2_init(pkt, &pkt_len, g_ent_port == 4500);
        } else {
            build_raw_l2tp_sccrq(pkt, &pkt_len);
        }
        send(fd, pkt, pkt_len, 0);

        while (g_ent_running && g_ent_sock_fd == fd) {
            struct pollfd pfd;
            pfd.fd = fd;
            pfd.events = POLLIN;
            int pr = poll(&pfd, 1, 15000);
            if (!g_ent_running || g_ent_sock_fd != fd) break;
            if (pr > 0 && (pfd.revents & POLLIN)) {
                unsigned char rbuf[1500];
                (void)recv(fd, rbuf, sizeof(rbuf), MSG_DONTWAIT);
            } else {
                // Raw NAT-T / tunnel keepalive byte
                unsigned char ka = 0xFF;
                send(fd, &ka, 1, MSG_DONTWAIT);
            }
        }
        if (g_ent_sock_fd == fd) {
            close(fd);
            g_ent_sock_fd = -1;
        }
    } else {
        // Raw TCP AnyConnect CSTP session in C core
        int fd = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
        if (fd < 0) {
            g_ent_running = 0;
            return NULL;
        }
        int one = 1;
        setsockopt(fd, IPPROTO_TCP, TCP_NODELAY, &one, sizeof(one));
        setsockopt(fd, SOL_SOCKET, SO_KEEPALIVE, &one, sizeof(one));
        g_ent_sock_fd = fd;

        struct sockaddr_in dst;
        memset(&dst, 0, sizeof(dst));
        dst.sin_family = AF_INET;
        dst.sin_port = htons((uint16_t)(g_ent_port > 0 ? g_ent_port : 443));
        dst.sin_addr = addr;

        int flags = fcntl(fd, F_GETFL, 0);
        fcntl(fd, F_SETFL, flags | O_NONBLOCK);
        connect(fd, (struct sockaddr*)&dst, sizeof(dst));

        struct pollfd pfd;
        pfd.fd = fd;
        pfd.events = POLLOUT;
        if (poll(&pfd, 1, 5000) > 0) {
            fcntl(fd, F_SETFL, flags);
            while (g_ent_running && g_ent_sock_fd == fd) {
                pfd.fd = fd;
                pfd.events = POLLIN;
                int r = poll(&pfd, 1, 20000);
                if (!g_ent_running || g_ent_sock_fd != fd) break;
                if (r > 0 && (pfd.revents & POLLIN)) {
                    unsigned char buf[1024];
                    if (recv(fd, buf, sizeof(buf), MSG_DONTWAIT) <= 0) break;
                }
            }
        }
        if (g_ent_sock_fd == fd) {
            close(fd);
            g_ent_sock_fd = -1;
        }
    }
    return NULL;
}

JNIEXPORT jint JNICALL
Java_com_alan_ripdpi_core_NativeOobBridge_sendOobInlineByte(JNIEnv *env, jobject thiz, jint fd, jbyte oob_byte) {
    (void)env; (void)thiz;
    unsigned char b = (unsigned char)oob_byte;
    ssize_t n = send((int)fd, &b, 1, MSG_OOB);
    return (n == 1) ? 0 : -errno;
}

JNIEXPORT jint JNICALL
Java_com_alan_ripdpi_core_NativeOobBridge_sendSplitWithOob(
    JNIEnv *env,
    jobject thiz,
    jint fd,
    jbyteArray payload,
    jint split_pos,
    jbyte oob_byte,
    jint post_delay_us
) {
    (void)thiz;
    if (!payload) return -1;
    jsize total_len = (*env)->GetArrayLength(env, payload);
    if (total_len <= 0) return 0;
    jbyte *buf = (*env)->GetByteArrayElements(env, payload, NULL);
    if (!buf) return -2;

    int sock = (int)fd;
    int one = 1;
    setsockopt(sock, IPPROTO_TCP, TCP_NODELAY, &one, sizeof(one));

    int sp = (int)split_pos;
    if (sp < 1) sp = 1;
    if (sp >= total_len) sp = total_len / 2;

    ssize_t s1 = send(sock, buf, (size_t)sp, 0);
    if (s1 < 0) {
        (*env)->ReleaseByteArrayElements(env, payload, buf, JNI_ABORT);
        return -errno;
    }

    unsigned char ob = (unsigned char)oob_byte;
    (void)send(sock, &ob, 1, MSG_OOB);

    if (post_delay_us > 0 && post_delay_us <= 50000) {
        usleep((useconds_t)post_delay_us);
    }

    ssize_t s2 = send(sock, buf + sp, (size_t)(total_len - sp), 0);
    (*env)->ReleaseByteArrayElements(env, payload, buf, JNI_ABORT);
    return (s2 >= 0) ? (jint)(s1 + s2) : -errno;
}

JNIEXPORT jint JNICALL
Java_com_alan_ripdpi_core_NativeOobBridge_tuneSocketLowLatency(JNIEnv *env, jobject thiz, jint fd, jint send_buf_sz) {
    (void)env; (void)thiz;
    int sock = (int)fd;
    int one = 1;
    setsockopt(sock, IPPROTO_TCP, TCP_NODELAY, &one, sizeof(one));
    setsockopt(sock, SOL_SOCKET, SO_KEEPALIVE, &one, sizeof(one));
    if (send_buf_sz > 0) {
        int sz = (int)send_buf_sz;
        setsockopt(sock, SOL_SOCKET, SO_SNDBUF, &sz, sizeof(sz));
    }
    return 0;
}

JNIEXPORT jint JNICALL
Java_com_alan_ripdpi_core_NativeOobBridge_nativeEnterpriseProbe(
    JNIEnv *env,
    jobject thiz,
    jstring jhost,
    jint port,
    jint proto_type,
    jint timeout_ms
) {
    (void)thiz;
    if (!jhost) return -1;
    const char *host = (*env)->GetStringUTFChars(env, jhost, NULL);
    if (!host) return -1;

    struct in_addr addr;
    if (resolve_ipv4(host, &addr) != 0) {
        (*env)->ReleaseStringUTFChars(env, jhost, host);
        return -1;
    }
    (*env)->ReleaseStringUTFChars(env, jhost, host);

    int tmo = (timeout_ms > 150) ? (int)timeout_ms : 1000;

    // 1. If IKEv2 (1) or L2TP (2), send raw C UDP protocol packet first
    if (proto_type == 1 || proto_type == 2) {
        int ufd = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
        if (ufd >= 0) {
            struct sockaddr_in dst;
            memset(&dst, 0, sizeof(dst));
            dst.sin_family = AF_INET;
            dst.sin_port = htons((uint16_t)(port > 0 ? port : (proto_type == 2 ? 1701 : 500)));
            dst.sin_addr = addr;

            unsigned char pkt[64];
            int pkt_len = 0;
            if (proto_type == 1) {
                build_raw_ikev2_init(pkt, &pkt_len, port == 4500);
            } else {
                build_raw_l2tp_sccrq(pkt, &pkt_len);
            }

            long long t0 = now_ms();
            sendto(ufd, pkt, (size_t)pkt_len, 0, (struct sockaddr*)&dst, sizeof(dst));
            struct pollfd pfd;
            pfd.fd = ufd;
            pfd.events = POLLIN;
            int pr = poll(&pfd, 1, tmo / 2);
            if (pr > 0 && (pfd.revents & POLLIN)) {
                unsigned char rbuf[512];
                ssize_t rn = recv(ufd, rbuf, sizeof(rbuf), MSG_DONTWAIT);
                close(ufd);
                if (rn > 0) {
                    int rtt = (int)(now_ms() - t0);
                    return (rtt > 0) ? rtt : 1;
                }
            } else {
                close(ufd);
            }
        }
    }

    // 2. Raw C non-blocking SYN/RST RTT probe
    int tfd = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (tfd < 0) return -1;
    int flags = fcntl(tfd, F_GETFL, 0);
    fcntl(tfd, F_SETFL, flags | O_NONBLOCK);

    struct sockaddr_in sin;
    memset(&sin, 0, sizeof(sin));
    sin.sin_family = AF_INET;
    sin.sin_port = htons((uint16_t)(port > 0 ? port : 443));
    sin.sin_addr = addr;

    long long t0 = now_ms();
    int rc = connect(tfd, (struct sockaddr*)&sin, sizeof(sin));
    if (rc == 0) {
        close(tfd);
        int rtt = (int)(now_ms() - t0);
        return (rtt > 0) ? rtt : 1;
    }
    if (errno != EINPROGRESS) {
        close(tfd);
        return -1;
    }

    struct pollfd pfd;
    pfd.fd = tfd;
    pfd.events = POLLOUT | POLLERR | POLLHUP;
    int pr = poll(&pfd, 1, tmo);
    int elapsed = (int)(now_ms() - t0);
    if (pr > 0) {
        int so_err = 0;
        socklen_t slen = sizeof(so_err);
        getsockopt(tfd, SOL_SOCKET, SO_ERROR, &so_err, &slen);
        close(tfd);
        // For UDP protocols (IKEv2/L2TP), ECONNREFUSED on TCP proves the remote gateway IP is alive in 1 exact RTT!
        if (so_err == 0 || ((proto_type == 1 || proto_type == 2) && so_err == ECONNREFUSED && elapsed < (tmo - 60))) {
            return (elapsed > 0) ? elapsed : 1;
        }
        return -1;
    }
    close(tfd);
    return -1;
}

JNIEXPORT jint JNICALL
Java_com_alan_ripdpi_core_NativeOobBridge_nativeStartEnterpriseRaw(
    JNIEnv *env,
    jobject thiz,
    jstring jhost,
    jint port,
    jint proto_type
) {
    (void)thiz;
    if (!jhost) return -1;
    const char *host = (*env)->GetStringUTFChars(env, jhost, NULL);
    if (!host) return -1;

    g_ent_running = 0;
    if (g_ent_sock_fd >= 0) {
        close(g_ent_sock_fd);
        g_ent_sock_fd = -1;
    }

    strncpy(g_ent_host, host, sizeof(g_ent_host) - 1);
    g_ent_host[sizeof(g_ent_host) - 1] = '\0';
    g_ent_port = (int)port;
    g_ent_proto = (int)proto_type;
    (*env)->ReleaseStringUTFChars(env, jhost, host);

    g_ent_running = 1;
    if (pthread_create(&g_ent_thread, NULL, enterprise_raw_worker, NULL) == 0) {
        pthread_detach(g_ent_thread);
        return 0;
    }
    g_ent_running = 0;
    return -1;
}

JNIEXPORT void JNICALL
Java_com_alan_ripdpi_core_NativeOobBridge_nativeStopEnterpriseRaw(JNIEnv *env, jobject thiz) {
    (void)env; (void)thiz;
    g_ent_running = 0;
    int fd = g_ent_sock_fd;
    g_ent_sock_fd = -1;
    if (fd >= 0) {
        close(fd);
    }
}
