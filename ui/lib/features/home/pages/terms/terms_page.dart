import 'package:flutter/material.dart';
// 条款页面
class TermsPage extends StatelessWidget {
  final String title;
  final String content;

  TermsPage({required this.title, required this.content});

  @override
  Widget build(BuildContext context) {
    const primaryBlack = Color(0xFF333333);
    const darkGrey = Color(0xFF666666);
    
    return Scaffold(
      appBar: AppBar(
        title: Text(
          title,
          style: TextStyle(
            color: primaryBlack, 
            fontWeight: FontWeight.w600,
            fontSize: 18.0,
          ),
        ),
        leading: IconButton(
          icon: Icon(Icons.arrow_back_ios),
          onPressed: () {
            Navigator.pop(context); // 返回上一个页面
          },
        ),
      ),
      body: Padding(
        padding: const EdgeInsets.all(16.0),
        child: SingleChildScrollView(
          child: Text(
            content,
            style: TextStyle(fontSize: 12.0, color: darkGrey),
          ),
        ),
      ),
    );
  }
}