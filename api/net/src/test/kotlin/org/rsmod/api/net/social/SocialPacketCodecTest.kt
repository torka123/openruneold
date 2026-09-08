package org.rsmod.api.net.social

import io.netty.buffer.Unpooled
import kotlin.test.Test
import kotlin.test.assertContentEquals
import net.rsprot.buffer.JagByteBuf
import net.rsprot.crypto.cipher.NopStreamCipher
import net.rsprot.protocol.game.outgoing.clan.ClanChannelDelta
import net.rsprot.protocol.game.outgoing.clan.ClanChannelFull
import net.rsprot.protocol.game.outgoing.clan.ClanSettingsDelta
import net.rsprot.protocol.game.outgoing.clan.ClanSettingsFull
import net.rsprot.protocol.game.outgoing.codec.clan.ClanChannelDeltaEncoder
import net.rsprot.protocol.game.outgoing.codec.clan.ClanChannelFullEncoder
import net.rsprot.protocol.game.outgoing.codec.clan.ClanSettingsDeltaEncoder
import net.rsprot.protocol.game.outgoing.codec.clan.ClanSettingsFullEncoder
import net.rsprot.protocol.game.outgoing.codec.friendchat.UpdateFriendChatChannelFullV2Encoder
import net.rsprot.protocol.game.outgoing.codec.friendchat.UpdateFriendChatChannelSingleUserEncoder
import net.rsprot.protocol.game.outgoing.friendchat.UpdateFriendChatChannelFull.FriendChatEntry
import net.rsprot.protocol.game.outgoing.friendchat.UpdateFriendChatChannelFullV2
import net.rsprot.protocol.game.outgoing.friendchat.UpdateFriendChatChannelSingleUser

/** Golden payload fixtures copied from the revision-240 rsprot encoder layouts. */
class SocialPacketCodecTest {
    @Test
    fun `legacy full channel join and leave payloads match revision 240`() {
        val join =
            UpdateFriendChatChannelFullV2(
                UpdateFriendChatChannelFullV2.JoinUpdate(
                    channelOwner = "Owner",
                    channelNameBase37 = 0x0102030405060708L,
                    kickRank = 3,
                    entries = listOf(FriendChatEntry("Bob", 301, 2, "World 301")),
                )
            )
        assertContentEquals(
            hex(
                "4f 77 6e 65 72 00 " +
                    "01 02 03 04 05 06 07 08 03 02 " +
                    "42 6f 62 00 01 2d 02 57 6f 72 6c 64 20 33 30 31 00"
            ),
            encode { UpdateFriendChatChannelFullV2Encoder().encode(NopStreamCipher, it, join) },
        )
        assertContentEquals(
            byteArrayOf(),
            encode {
                UpdateFriendChatChannelFullV2Encoder()
                    .encode(
                        NopStreamCipher,
                        it,
                        UpdateFriendChatChannelFullV2(UpdateFriendChatChannelFullV2.LeaveUpdate),
                    )
            },
        )
    }

    @Test
    fun `legacy member add and remove payloads match revision 240`() {
        val added =
            UpdateFriendChatChannelSingleUser(
                UpdateFriendChatChannelSingleUser.AddedFriendChatUser("Bob", 301, 2, "World 301")
            )
        assertContentEquals(
            hex("42 6f 62 00 01 2d 02 57 6f 72 6c 64 20 33 30 31 00"),
            encode { UpdateFriendChatChannelSingleUserEncoder().encode(NopStreamCipher, it, added) },
        )

        val removed =
            UpdateFriendChatChannelSingleUser(
                UpdateFriendChatChannelSingleUser.RemovedFriendChatUser("Bob", 301)
            )
        assertContentEquals(
            hex("42 6f 62 00 01 2d 80"),
            encode {
                UpdateFriendChatChannelSingleUserEncoder().encode(NopStreamCipher, it, removed)
            },
        )
    }

    @Test
    fun `own and guest clan channel payloads match revision 240`() {
        val join =
            ClanChannelFull(
                clanType = 0,
                update =
                    ClanChannelFull.JoinUpdate(
                        clanHash = 1L,
                        updateNum = 2L,
                        clanName = "Clan",
                        discardedBoolean = false,
                        kickRank = 100,
                        talkRank = 0,
                        members = listOf(ClanChannelFull.ClanMember("Bob", 3, 301)),
                    ),
            )
        assertContentEquals(
            hex(
                "00 02 " +
                    "00 00 00 00 00 00 00 01 " +
                    "00 00 00 00 00 00 00 02 " +
                    "43 6c 61 6e 00 00 64 00 00 01 " +
                    "42 6f 62 00 03 01 2d"
            ),
            encode { ClanChannelFullEncoder().encode(NopStreamCipher, it, join) },
        )
        assertContentEquals(
            hex("ff"),
            encode {
                ClanChannelFullEncoder()
                    .encode(NopStreamCipher, it, ClanChannelFull(-1, ClanChannelFull.LeaveUpdate))
            },
        )
    }

    @Test
    fun `clan settings full and clear payloads match revision 240`() {
        val join =
            ClanSettingsFull(
                clanType = 0,
                update =
                    ClanSettingsFull.JoinUpdate(
                        updateNum = 1,
                        creationTime = 2,
                        clanName = "Clan",
                        allowUnaffined = true,
                        talkRank = 0,
                        kickRank = 100,
                        lootshareRank = 126,
                        coinshareRank = 126,
                        affinedMembers =
                            listOf(ClanSettingsFull.AffinedClanMember("Bob", 3, 0, 10, false)),
                        bannedMembers = listOf(ClanSettingsFull.BannedClanMember("Bad")),
                        settings = emptyList(),
                    ),
            )
        assertContentEquals(
            hex(
                "00 06 02 00 00 00 01 00 00 00 02 00 01 01 " +
                    "43 6c 61 6e 00 00 00 00 00 01 00 64 7e 7e " +
                    "42 6f 62 00 03 00 00 00 00 00 0a 00 " +
                    "42 61 64 00 00 00"
            ),
            encode { ClanSettingsFullEncoder().encode(NopStreamCipher, it, join) },
        )
        assertContentEquals(
            hex("ff"),
            encode {
                ClanSettingsFullEncoder()
                    .encode(NopStreamCipher, it, ClanSettingsFull(-1, ClanSettingsFull.LeaveUpdate))
            },
        )
    }

    @Test
    fun `clan channel member add update and removal delta matches revision 240`() {
        val delta =
            ClanChannelDelta(
                clanType = 0,
                key = 0x0102030405060708L,
                updateNum = 0x1112131415161718L,
                events =
                    listOf(
                        ClanChannelDelta.AddUserEvent("Bob", 301, 3),
                        ClanChannelDelta.UpdateUserDetailsEvent(1, "Ann", 4, 302),
                        ClanChannelDelta.DeleteUserEvent(2),
                    ),
            )
        assertContentEquals(
            hex(
                "00 01 02 03 04 05 06 07 08 11 12 13 14 15 16 17 18 " +
                    "01 ff 42 6f 62 00 01 2d 03 00 00 00 00 00 00 00 00 " +
                    "02 00 01 04 01 2e 00 00 00 00 00 00 00 00 41 6e 6e 00 " +
                    "03 00 02 00 ff 00"
            ),
            encode { ClanChannelDeltaEncoder().encode(NopStreamCipher, it, delta) },
        )
    }

    @Test
    fun `clan settings rank mute member removal and ban deltas match revision 240`() {
        val delta =
            ClanSettingsDelta(
                clanType = 0,
                owner = 0L,
                updateNum = 2,
                updates =
                    listOf(
                        ClanSettingsDelta.SetMemberRankUpdate(1, 4),
                        ClanSettingsDelta.SetMemberMutedUpdate(1, true),
                        ClanSettingsDelta.DeleteMemberUpdate(2),
                        ClanSettingsDelta.AddBannedUpdate("Bad"),
                        ClanSettingsDelta.DeleteBannedUpdate(0),
                    ),
            )
        assertContentEquals(
            hex(
                "00 00 00 00 00 00 00 00 00 00 00 00 02 " +
                    "02 00 01 04 0e 00 01 01 05 00 02 " +
                    "03 ff 42 61 64 00 06 00 00 00"
            ),
            encode { ClanSettingsDeltaEncoder().encode(NopStreamCipher, it, delta) },
        )
    }

    @Test
    fun `legacy chat message payload matches revision 240`() {
        val message =
            net.rsprot.protocol.game.outgoing.friendchat.MessageFriendChannel(
                sender = "Bob",
                channelNameBase37 = 0x0102030405060708L,
                worldId = 301,
                worldMessageCounter = 0x010203,
                chatCrownType = 2,
                message = "Hi",
            )
        assertContentEquals(
            hex("42 6f 62 00 " + "01 02 03 04 05 06 07 08 " + "01 2d 01 02 03 02 02 48 69"),
            encode {
                net.rsprot.protocol.game.outgoing.codec.friendchat
                    .MessageFriendChannelEncoder(testHuffmanProvider())
                    .encode(NopStreamCipher, it, message)
            },
        )
    }

    @Test
    fun `own and guest clan message payloads match revision 240`() {
        fun packet(clanType: Int): net.rsprot.protocol.game.outgoing.clan.MessageClanChannel =
            net.rsprot.protocol.game.outgoing.clan.MessageClanChannel(
                clanType = clanType,
                name = "Bob",
                worldId = 301,
                worldMessageCounter = 0x010203,
                chatCrownType = 2,
                message = "Hi",
            )

        assertContentEquals(
            hex("00 42 6f 62 00 01 2d 01 02 03 02 02 48 69"),
            encode {
                net.rsprot.protocol.game.outgoing.codec.clan
                    .MessageClanChannelEncoder(testHuffmanProvider())
                    .encode(NopStreamCipher, it, packet(0))
            },
        )
        assertContentEquals(
            hex("ff 42 6f 62 00 01 2d 01 02 03 02 02 48 69"),
            encode {
                net.rsprot.protocol.game.outgoing.codec.clan
                    .MessageClanChannelEncoder(testHuffmanProvider())
                    .encode(NopStreamCipher, it, packet(-1))
            },
        )
    }

    private fun encode(writer: (JagByteBuf) -> Unit): ByteArray {
        val byteBuf = Unpooled.buffer()
        try {
            writer(JagByteBuf(byteBuf))
            val output = ByteArray(byteBuf.readableBytes())
            byteBuf.getBytes(byteBuf.readerIndex(), output)
            return output
        } finally {
            byteBuf.release()
        }
    }

    private fun hex(value: String): ByteArray =
        value.trim().split(Regex("\\s+")).map { it.toInt(16).toByte() }.toByteArray()

    private fun testHuffmanProvider(): net.rsprot.compression.provider.DefaultHuffmanCodecProvider {
        val lengths = Unpooled.wrappedBuffer(ByteArray(256) { 8 })
        return net.rsprot.compression.provider.DefaultHuffmanCodecProvider(
            net.rsprot.compression.HuffmanCodec.create(lengths)
        )
    }
}
