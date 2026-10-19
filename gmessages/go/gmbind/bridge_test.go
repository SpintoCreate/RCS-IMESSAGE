package gmbind

import (
	"strings"
	"testing"
)

func TestRejectsMissingCookiesBeforeNetwork(t *testing.T) {
	b := NewBridge()
	_, err := b.BeginPairing(`{"SID":"placeholder"}`)
	if err == nil || !strings.Contains(err.Error(), "HSID") {
		t.Fatalf("missing cookies should be rejected locally: %v", err)
	}
	if b.Status() != "disconnected" { t.Fatal("invalid input started a connection") }
}

func TestRequiresChosenConversationAndNonEmptyText(t *testing.T) {
	b := NewBridge()
	if err := b.SendText(0, " \n "); err == nil { t.Fatal("empty text was accepted") }
	if err := b.SendText(0, "test only"); err == nil { t.Fatal("missing conversation was accepted") }
}
