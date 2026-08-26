#!/usr/bin/env perl
use strict;
use warnings;
use Compress::Zlib qw(crc32);
use Compress::Raw::Zlib qw(Z_STREAM_END);

sub fail {
    my ($path, $message) = @_;
    print STDERR "PNG validation failed for $path: $message\n";
    exit 1;
}

@ARGV == 1 or die "Usage: validate-png.pl <image.png>\n";
my $path = $ARGV[0];
open my $file, '<:raw', $path or die "Cannot open $path: $!\n";
local $/;
my $data = <$file>;
close $file or die "Cannot close $path: $!\n";

defined $data && length($data) >= 20 or fail($path, 'file is truncated');
substr($data, 0, 8) eq "\x89PNG\x0d\x0a\x1a\x0a" or
    fail($path, 'invalid signature');

my %allowed = map { $_ => 1 } qw(IHDR sRGB sBIT IDAT IEND);
my $offset = 8;
my $chunk_index = 0;
my $ihdr_count = 0;
my $idat_count = 0;
my $iend_count = 0;
my $seen_idat = 0;
my $idat_finished = 0;
my %singleton_count;
my ($width, $height, $bit_depth, $color_type);
my $compressed = '';

while ($offset < length($data)) {
    length($data) - $offset >= 12 or fail($path, 'truncated chunk header');
    my $length = unpack('N', substr($data, $offset, 4));
    my $type = substr($data, $offset + 4, 4);
    $type =~ /^[A-Za-z]{4}$/ or fail($path, 'invalid chunk type');
    $allowed{$type} or fail($path, "metadata or unsupported chunk $type");
    $length <= 64 * 1024 * 1024 or fail($path, 'unreasonable chunk length');
    my $end = $offset + 12 + $length;
    $end <= length($data) or fail($path, "truncated $type chunk");

    my $payload = substr($data, $offset + 8, $length);
    my $expected_crc = unpack('N', substr($data, $offset + 8 + $length, 4));
    my $actual_crc = crc32($type . $payload);
    $actual_crc == $expected_crc or fail($path, "CRC mismatch in $type chunk");

    if ($type eq 'IHDR') {
        $chunk_index == 0 or fail($path, 'IHDR is not first');
        $length == 13 or fail($path, 'invalid IHDR length');
        ++$ihdr_count == 1 or fail($path, 'duplicate IHDR');
        ($width, $height, $bit_depth, $color_type,
            my $compression, my $filter, my $interlace) =
            unpack('NNCCCCC', $payload);
        $width > 0 && $height > 0 or fail($path, 'zero image dimension');
        $width <= 16384 && $height <= 16384 or
            fail($path, 'image dimensions exceed the reviewed limit');
        my %valid_depths = (
            0 => {map { $_ => 1 } qw(1 2 4 8 16)},
            2 => {map { $_ => 1 } qw(8 16)},
            4 => {map { $_ => 1 } qw(8 16)},
            6 => {map { $_ => 1 } qw(8 16)},
        );
        exists $valid_depths{$color_type} && $valid_depths{$color_type}{$bit_depth} or
            fail($path, 'unsupported color type or bit depth');
        $compression == 0 && $filter == 0 or
            fail($path, 'unsupported compression or filter method');
        $interlace == 0 or fail($path, 'interlaced PNG is outside the reviewed subset');
    } elsif ($type eq 'sRGB') {
        $length == 1 or fail($path, 'invalid sRGB length');
        ++$singleton_count{$type} == 1 or fail($path, 'duplicate sRGB');
        !$seen_idat or fail($path, 'sRGB follows image data');
        unpack('C', $payload) <= 3 or fail($path, 'invalid sRGB rendering intent');
    } elsif ($type eq 'sBIT') {
        my %expected_length = (0 => 1, 2 => 3, 4 => 2, 6 => 4);
        $length == $expected_length{$color_type} or fail($path, 'invalid sBIT length');
        ++$singleton_count{$type} == 1 or fail($path, 'duplicate sBIT');
        !$seen_idat or fail($path, 'sBIT follows image data');
        for my $significant (unpack('C*', $payload)) {
            $significant > 0 && $significant <= $bit_depth or
                fail($path, 'invalid sBIT value');
        }
    } elsif ($type eq 'IDAT') {
        $ihdr_count == 1 or fail($path, 'IDAT precedes IHDR');
        $iend_count == 0 or fail($path, 'IDAT follows IEND');
        !$idat_finished or fail($path, 'non-contiguous IDAT chunks');
        $seen_idat = 1;
        ++$idat_count;
        $compressed .= $payload;
        length($compressed) <= 128 * 1024 * 1024 or
            fail($path, 'compressed image data exceeds the reviewed limit');
    } elsif ($type eq 'IEND') {
        $length == 0 or fail($path, 'invalid IEND length');
        $seen_idat or fail($path, 'IEND precedes IDAT');
        ++$iend_count == 1 or fail($path, 'duplicate IEND');
        $end == length($data) or fail($path, 'trailing data after IEND');
    }

    $idat_finished = 1 if $seen_idat && $type ne 'IDAT';

    $offset = $end;
    ++$chunk_index;
}

$ihdr_count == 1 or fail($path, 'missing IHDR');
$idat_count > 0 or fail($path, 'missing IDAT');
$iend_count == 1 or fail($path, 'missing IEND');

my %channels = (0 => 1, 2 => 3, 4 => 2, 6 => 4);
my $row_bytes = int(($width * $channels{$color_type} * $bit_depth + 7) / 8);
my $expected_size = ($row_bytes + 1) * $height;
$expected_size <= 512 * 1024 * 1024 or
    fail($path, 'decompressed image data exceeds the reviewed limit');
my ($inflater, $inflate_init) = Compress::Raw::Zlib::Inflate->new(-ConsumeInput => 1);
defined $inflater or fail($path, "cannot initialize zlib: $inflate_init");
my $pixels = '';
my $inflate_status = $inflater->inflate($compressed, $pixels);
$inflate_status == Z_STREAM_END or fail($path, 'invalid or incomplete zlib stream');
length($compressed) == 0 or fail($path, 'unused compressed payload after zlib stream');
length($pixels) == $expected_size or fail($path, 'decompressed scanline size mismatch');
for (my $row = 0; $row < $height; ++$row) {
    ord(substr($pixels, $row * ($row_bytes + 1), 1)) <= 4 or
        fail($path, 'invalid scanline filter byte');
}
exit 0;
