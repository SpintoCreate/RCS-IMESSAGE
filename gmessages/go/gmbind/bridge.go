// Package gmbind exposes only a small, personal Android feasibility spike.
// libgm remains an upstream dependency and is never copied into the app.
package gmbind

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"strings"
	"sync"
	"time"

	"github.com/google/uuid"
	"github.com/rs/zerolog"
	"go.mau.fi/util/exhttp"
	"go.mau.fi/mautrix-gmessages/pkg/libgm"
	"go.mau.fi/mautrix-gmessages/pkg/libgm/events"
	"go.mau.fi/mautrix-gmessages/pkg/libgm/gmproto"
)

var neededCookies = []string{"SID", "HSID", "SSID", "OSID", "APISID", "SAPISID"}

// Bridge holds one ephemeral linked-device session. Its caller encrypts SessionJSON.
type Bridge struct {
	mu       sync.Mutex
	client   *libgm.Client
	pairing  *libgm.PairingSession
	cancel   context.CancelFunc
	chats    []*gmproto.Conversation
	sims     map[string]*gmproto.SIMCard
	received int
	status   string
}

func NewBridge() *Bridge { return &Bridge{status: "disconnected"} }

func (b *Bridge) makeClient(ad *libgm.AuthData) *libgm.Client {
	// Upstream trace/debug events can contain raw message bytes, IDs, and tokens.
	// No libgm event or transport log is ever forwarded to Android.
	logger := zerolog.New(io.Discard).Level(zerolog.Disabled)
	client := libgm.NewClient(ad, nil, logger, exhttp.ClientSettings{})
	client.SetEventHandler(func(event any) {
		b.mu.Lock()
		defer b.mu.Unlock()
		switch item := event.(type) {
		case *libgm.WrappedMessage:
			if !item.IsOld && item.GetMessageStatus().GetStatus() == gmproto.MessageStatusType_INCOMING_COMPLETE {
				b.received++
			}
		case *gmproto.Settings:
			if b.sims == nil { b.sims = make(map[string]*gmproto.SIMCard) }
			for _, sim := range item.GetSIMCards() {
				if id := sim.GetSIMParticipant().GetID(); id != "" { b.sims[id] = sim }
			}
		case *events.PhoneNotResponding, *events.ListenFatalError, *events.GaiaLoggedOut:
			b.status = "disconnected"
		case *events.PhoneRespondingAgain, *events.ListenRecovered:
			b.status = "connected"
		}
	})
	return client
}

// BeginPairing accepts only the documented cookie names, never a cURL command.
func (b *Bridge) BeginPairing(cookieJSON string) (string, error) {
	var supplied map[string]string
	if err := json.Unmarshal([]byte(cookieJSON), &supplied); err != nil { return "", fmt.Errorf("invalid cookie file: %w", err) }
	cookies := make(map[string]string)
	for _, name := range neededCookies {
		value := strings.TrimSpace(supplied[name])
		if value == "" { return "", fmt.Errorf("required cookie %s is missing", name) }
		cookies[name] = value
	}
	if value := strings.TrimSpace(supplied["__Secure-1PSIDTS"]); value != "" { cookies["__Secure-1PSIDTS"] = value }
	b.Close()
	ad := libgm.NewAuthData()
	ad.SetCookies(cookies)
	client := b.makeClient(ad)
	ctx, cancel := context.WithCancel(context.Background())
	b.mu.Lock()
	b.client, b.cancel, b.status = client, cancel, "pairing"
	b.mu.Unlock()
	requestCtx, stop := context.WithTimeout(ctx, 60*time.Second)
	defer stop()
	if err := client.FetchConfig(requestCtx); err != nil { b.Close(); return "", fmt.Errorf("fetch Google Messages configuration: %w", err) }
	emoji, session, err := client.StartGaiaPairing(requestCtx, ctx)
	if err != nil { b.Close(); return "", fmt.Errorf("start linked-device pairing: %w", err) }
	b.mu.Lock()
	b.pairing = session
	b.mu.Unlock()
	return emoji, nil
}

func (b *Bridge) FinishPairing() error {
	b.mu.Lock()
	client, pairing, ctx := b.client, b.pairing, b.cancel
	b.mu.Unlock()
	if client == nil || pairing == nil || ctx == nil { return errors.New("no pairing is waiting for confirmation") }
	requestCtx, stop := context.WithTimeout(context.Background(), 120*time.Second)
	defer stop()
	if _, err := client.FinishGaiaPairing(requestCtx, pairing); err != nil { b.Close(); return fmt.Errorf("confirm phone pairing: %w", err) }
	if err := client.Connect(context.Background()); err != nil { b.Close(); return fmt.Errorf("connect paired device: %w", err) }
	b.mu.Lock()
	b.pairing, b.status = nil, "connected"
	b.mu.Unlock()
	return nil
}

// SessionJSON contains credentials. Callers must put it directly into Keystore-backed storage.
func (b *Bridge) SessionJSON() (string, error) {
	b.mu.Lock()
	client := b.client
	b.mu.Unlock()
	if client == nil || !client.IsLoggedIn() { return "", errors.New("not paired") }
	bytes, err := json.Marshal(client.AuthData)
	if err != nil { return "", fmt.Errorf("encode linked-device session: %w", err) }
	return string(bytes), nil
}

func (b *Bridge) Restore(sessionJSON string) error {
	var ad libgm.AuthData
	if err := json.Unmarshal([]byte(sessionJSON), &ad); err != nil { return fmt.Errorf("decode stored session: %w", err) }
	b.Close()
	client := b.makeClient(&ad)
	ctx, cancel := context.WithCancel(context.Background())
	b.mu.Lock()
	b.client, b.cancel, b.status = client, cancel, "connecting"
	b.mu.Unlock()
	if err := client.Connect(ctx); err != nil { b.Close(); return fmt.Errorf("restore linked-device connection: %w", err) }
	b.mu.Lock()
	b.status = "connected"
	b.mu.Unlock()
	return nil
}

func (b *Bridge) ListConversations() (int, error) {
	b.mu.Lock()
	client := b.client
	b.mu.Unlock()
	if client == nil { return 0, errors.New("not connected") }
	ctx, stop := context.WithTimeout(context.Background(), 60*time.Second)
	defer stop()
	response, err := client.ListConversations(ctx, &gmproto.ListConversationsRequest{Count: 30})
	if err != nil { return 0, fmt.Errorf("list conversations: %w", err) }
	b.mu.Lock()
	b.chats = response.GetConversations()
	count := len(b.chats)
	b.mu.Unlock()
	return count, nil
}

// SendText uses a 0-based index from the last ListConversations call. No IDs leave this API.
func (b *Bridge) SendText(index int, body string) error {
	if strings.TrimSpace(body) == "" { return errors.New("message is empty") }
	b.mu.Lock()
	if index < 0 || index >= len(b.chats) { b.mu.Unlock(); return errors.New("choose a listed conversation") }
	client, chat := b.client, b.chats[index]
	sim := b.sims[chat.GetDefaultOutgoingID()]
	b.mu.Unlock()
	if client == nil || sim == nil || sim.GetSIMData().GetSIMPayload() == nil {
		return errors.New("phone SIM settings have not arrived; keep Google Messages open and retry")
	}
	if chat.GetReadOnly() { return errors.New("conversation is read-only") }
	transactionID := uuid.NewString()
	request := &gmproto.SendMessageRequest{
		ConversationID: chat.GetConversationID(),
		MessagePayload: &gmproto.MessagePayload{
			TmpID: transactionID, TmpID2: transactionID,
			ConversationID: chat.GetConversationID(), ParticipantID: chat.GetDefaultOutgoingID(),
			MessageInfo: []*gmproto.MessageInfo{{Data: &gmproto.MessageInfo_MessageContent{MessageContent: &gmproto.MessageContent{Content: body}}}},
		},
		SIMPayload: sim.GetSIMData().GetSIMPayload(), TmpID: transactionID,
	}
	ctx, stop := context.WithTimeout(context.Background(), 90*time.Second)
	defer stop()
	response, err := client.SendMessage(ctx, request)
	if err != nil { return fmt.Errorf("send text to phone: %w", err) }
	if response.GetStatus() != gmproto.SendMessageResponse_SUCCESS {
		return fmt.Errorf("phone rejected text: %s", response.GetStatus().String())
	}
	return nil
}

func (b *Bridge) ReceivedCount() int { b.mu.Lock(); defer b.mu.Unlock(); return b.received }
func (b *Bridge) Status() string { b.mu.Lock(); defer b.mu.Unlock(); return b.status }

func (b *Bridge) Close() {
	b.mu.Lock()
	client, cancel := b.client, b.cancel
	b.client, b.cancel, b.pairing, b.chats, b.sims, b.status = nil, nil, nil, nil, nil, "disconnected"
	b.mu.Unlock()
	if cancel != nil { cancel() }
	if client != nil { client.Disconnect() }
}
