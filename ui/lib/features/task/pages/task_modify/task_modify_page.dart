import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:ui/widgets/card/screenshot_card.dart';

class TaskModifyPage extends StatelessWidget {
  final String? taskId;
  final String? type;
  final String? title;
  final String? dateTime;
  final String? tag;
  final String? content;
  final String? imagePath;
  final Map<String, dynamic>? payload;

  const TaskModifyPage({
    Key? key,
    this.taskId,
    this.type,
    this.title,
    this.dateTime,
    this.tag,
    this.content,
    this.imagePath,
    this.payload,
  }) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.white,
      appBar: AppBar(
        backgroundColor: Colors.white,
        elevation: 0,
        automaticallyImplyLeading: false,
        flexibleSpace: SafeArea(
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                const Text(
                  '任务修改',
                  style: TextStyle(
                    color: Colors.black,
                    fontSize: 18,
                    fontWeight: FontWeight.bold,
                  ),
                ),
                IconButton(
                  icon: const Icon(Icons.close, color: Colors.black),
                  onPressed: () => context.pop(),
                ),
              ],
            ),
          ),
        ),
      ),
      body: SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // 详细内容
            if (content != null)
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                child: Text(
                  content!,
                  style: const TextStyle(
                    color: Colors.black,
                    fontSize: 14,
                    height: 1.6,
                  ),
                ),
              ),
            
            const SizedBox(height: 100), // 底部留白
          ],
        ),
      ),
    );
  }
}
