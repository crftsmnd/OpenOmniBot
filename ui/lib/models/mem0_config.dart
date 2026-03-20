class Mem0Config {
  final String baseUrl;
  final String apiKey;
  final String agentId;
  final bool configured;
  final String? source;
  final int? updatedAt;
  final String? userId;

  const Mem0Config({
    required this.baseUrl,
    required this.apiKey,
    required this.agentId,
    this.configured = false,
    this.source,
    this.updatedAt,
    this.userId,
  });

  bool get isConfigured =>
      configured || (baseUrl.trim().isNotEmpty && apiKey.trim().isNotEmpty);

  factory Mem0Config.fromMap(Map<dynamic, dynamic> raw) {
    return Mem0Config(
      baseUrl: (raw['baseUrl'] ?? '').toString(),
      apiKey: (raw['apiKey'] ?? '').toString(),
      agentId: (raw['agentId'] ?? '').toString(),
      configured: raw['configured'] == true,
      source: raw['source']?.toString(),
      updatedAt: raw['updatedAt'] is int
          ? raw['updatedAt'] as int
          : int.tryParse((raw['updatedAt'] ?? '').toString()),
      userId: raw['userId']?.toString(),
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'baseUrl': baseUrl,
      'apiKey': apiKey,
      'agentId': agentId,
      'configured': configured,
      'source': source,
      'updatedAt': updatedAt,
      'userId': userId,
    };
  }

  Mem0Config copyWith({
    String? baseUrl,
    String? apiKey,
    String? agentId,
    bool? configured,
    String? source,
    int? updatedAt,
    String? userId,
  }) {
    return Mem0Config(
      baseUrl: baseUrl ?? this.baseUrl,
      apiKey: apiKey ?? this.apiKey,
      agentId: agentId ?? this.agentId,
      configured: configured ?? this.configured,
      source: source ?? this.source,
      updatedAt: updatedAt ?? this.updatedAt,
      userId: userId ?? this.userId,
    );
  }
}
