"""

You need to build a permanent key-value store. This store must be able to save data to a file system and read it 
back later. You will get a "mock" (fake) file system and some tools to turn numbers and strings into bytes.

The main difficulty is inventing your own format to save dictionaries. You cannot use easy tools like JSON or pickle.
Your format must handle strings that contain strange characters.

Important Context:

You get a fake file system, not a real one.
You get helper functions to change integers and strings into bytes.
No JSON or pickle allowed.
Keys and values can have any characters (like new lines, emojis, or commas).
You must be able to load the data back exactly as it was saved.

class FileSystem:
    def save_blob(self, data: bytes) -> None:
    def get_blob(self) -> bytes:

# Helper functions provided to you
def serialize_int(value: int) -> bytes:

def deserialize_int(data: bytes) -> int:

def serialize_str(value: str) -> bytes:

def deserialize_str(data: bytes) -> str:

"""
class FileSystem:
    def save_blob(self, data: bytes) -> None:
        pass
    def get_blob(self) -> bytes:
        pass

def serialize_int(value: int) -> bytes:
    pass
def deserialize_int(data: bytes) -> int:
    pass
def serialize_str(value: str) -> bytes:
    pass
def deserialize_str(data: bytes) -> str:
    pass

class KVStore:
    def __init__(self, file_system: FileSystem):
        """Start with a file system instance"""
        self.fs = file_system
        self.store = {}
        self.CHUNK_SIZE = 1024  # Max 1KB per file
        self.METADATA_FILE = "_metadata"
        self.CHUNK_PREFIX = "chunk_"
        
    def put(self, key: str, value: str) -> None:
        """Save a key-value pair in memory"""
        self.store[key] = value

    def get(self, key: str) -> str:
        """Find a value using a key"""
        return self.store[key]

    def shutdown(self) -> None:
        """Turn the whole store into bytes and save it"""
        serialized_bytes = self._serialize()

        if not serialized_bytes:
            metadata = serialize_int(0)
            self.fs.save_blob(self.METADATA_FILE, metadata)
        
        total_chunks = (len(serialized_bytes) + self.CHUNK_SIZE - 1) // self.CHUNK_SIZE
        metadata = serialize_int(total_chunks)
        self.fs.save_blob(self.METADATA_FILE, metadata)

        for idx in range(total_chunks):
            start_pos = idx * self.CHUNK_SIZE
            end_pos = min(start_pos + self.CHUNK_SIZE, len(serialized_bytes))
            chunk_data = serialized_bytes[start_pos:end_pos]

            chunk_filename = f"{self.CHUNK_PREFIX}{idx}"
            self.fs.save_blob(chunk_filename, chunk_data)

    def restore(self) -> None:
        """Load bytes from the file and rebuild the store"""
        # Read metadata to find out how many files to load
        metadata_bytes = self.fs.get_blob(self.METADATA_FILE)
        total_chunks = deserialize_int(metadata_bytes)

        if total_chunks == 0:
            self.store = {}
            return

        all_data = b""
        for i in range(total_chunks):
            chunk_filename = f"{self.CHUNK_PREFIX}{i}"
            chunk_data = self.fs.get_blob(chunk_filename)
            all_data += chunk_data

        # Turn the full data back into a dictionary
        self.store = self._deserialize(all_data)

        #self.store = self._deserialize(self.fs.get_blob())

    def _serialize(self) -> bytes:
        """
        Convert dictionary to bytes.
        Format: <keyLen>:<key><valueLen>:<value>
        """
        if not self.store:
            return b""

        result = []
        for key, value in self.store.items():
            # Process key
            key_bytes = serialize_str(key)
            key_len_bytes = serialize_int(len(key_bytes))

            # Process value
            value_bytes = serialize_str(value)
            value_len_bytes = serialize_int(len(value_bytes))

            # Combine: len + key + len + value
            result.append(key_len_bytes)
            result.append(key_bytes)
            result.append(value_len_bytes)
            result.append(value_bytes)

        return b"".join(result)

    def _deserialize(self, data: bytes) -> dict:
        """
        Turn bytes back into dictionary using the length format.
        """
        store = {}
        pos = 0

        while pos < len(data):
            # Read key size and key data
            key, pos = self._read_length_and_data(data, pos)

            # Read value size and value data
            value, pos = self._read_length_and_data(data, pos)

            store[key] = value

        return store

    def _read_length_and_data(self, data: bytes, pos: int) -> tuple:
        """
        Helper to read one piece of data.
        Returns (string_data, new_position_pointer)
        """
        # Read the length (assuming 4 bytes for an integer)
        length_bytes = data[pos:pos+4]
        length = deserialize_int(length_bytes)
        pos += 4

        # Read the actual string data
        data_bytes = data[pos:pos+length]
        data_str = deserialize_str(data_bytes)
        pos += length

        return data_str, pos
