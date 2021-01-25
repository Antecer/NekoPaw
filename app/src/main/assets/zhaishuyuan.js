// 需要传递到外部的数据(必要)
var baseObject = {
	info: {
		site: '源名称',
		type: '源类型',
		origin: '主页',
		group: '分组'
	},
	search: [{ title: '书名', author: '作者', intro: '简介', tag: '分类', count: '字数', img: '封面', date: '更新日期', url: '详情页' }],
	detail: { title: '书名', author: '作者', intro: '简介', tag: '分类', count: '字数', img: '封面', date: '更新日期', url: '目录页' },
	chapter: [{ title: '标题', time: '更新日期', url: '正文页' }],
	context: ''
};

// 源信息
baseObject.info = {
	origin: 'https://www.zhaishuyuan.com',
	type: 'book',
	site: '💮斋书苑',
	group: '更新快;无错字'
};
// 判断详情页
var isDetail = '';

// 搜索页
function search(searchKey) {
	console.info(`开始搜索关键字 ${searchKey}`);
	let response = fetch(`${baseObject.info.origin}/search/`, {
		method: 'POST',
		headers: {
			'content-type': 'application/x-www-form-urlencoded'
		},
		body: `key=${UrlEncoder(searchKey, 'gbk')}`
	});
	let html = response.text();
	let document = new Document(html);
	console.info('成功获取结果');

	baseObject.search = [];
	let searchList = document.querySelectorAll('#sitembox dl');
	let titleList = searchList.queryAllText('h3>a');
	console.info(`解析到 ${titleList.length} 个结果`);
	if (titleList.length == 0) {
		isDetail = document;
		baseObject.search.push({});
		console.info(`尝试作为详情页解析`);
		return;
	} else isDetail = '';
	let authorList = searchList.queryAllText('span:nth-child(1)');
	let introList = searchList.queryAllText('.book_des');
	let tagList = searchList.queryAllText('span:nth-child(3)');
	let countList = searchList.queryAllText('span:nth-child(4)');
	let imgList = searchList.queryAllAttr('img', '_src');
	let dateList = searchList.queryAllText('dd:last-child>span');
	let urlList = searchList.queryAllAttr('dt>a', 'href');
	for (let i = 0, n = titleList.length; i < n; ++i) {
		baseObject.search.push({
			title: titleList[i],
			author: authorList[i],
			intro: introList[i],
			tag: tagList[i],
			count: countList[i],
			img: imgList[i],
			date: dateList[i],
			url: urlList[i]
		});
	}
	console.info(JSON.stringify(baseObject.search[0]));
	console.info(`搜索页解析完成\n\n`);
}

function detail(url) {
	let document = isDetail;
	if (!document) {
		console.info(`开始获取详情页 ${url}`);
		let response = fetch(url);
		let html = response.text();
		document = new Document(html);
		console.info('成功获取结果');
	}

	baseObject.detail = {
		title: document.queryAttr('[property="og:novel:book_name"]', 'content'),
		author: document.queryAttr('[property="og:novel:author"]', 'content'),
		intro: document.queryText('#bookintro'),
		tag: document.queryAttr('[property="og:novel:category"]', 'content'),
		count: document.queryText('.count li:last-child>span'),
		img: document.queryAttr('[property="og:image"]', 'content'),
		date: document.queryAttr('[property="og:novel:update_time"]', 'content'),
		url: document.queryAttr('[property="og:novel:read_url"]', 'content')
	};
	console.info(`详情页解析完成`);
	console.info(JSON.stringify(baseObject.detail)+`\n\n`);
}

function chapter(url) {
	console.info(`开始获取目录页 ${url}`);
	let response = fetch(url);
	let html = response.text();
	let document = new Document(html);
	console.info('成功获取结果');

	let bid = parseInt(html.match(/data-bid="(\d+)/)[1]);
	let reg = 'href="/chapter/[^/]+/([^"]+)[^>]+>([^<]+)[^>]+>([^<]+)';
	baseObject.chapter = html.match(new RegExp(reg, 'g')).map((item) => {
		let ret = item.match(reg);
		return { cN: ret[2], uT: ret[3].trim(), id: parseInt(ret[1]) + bid };
	});
	let hider = html.match(/查看隐藏章节[^<]+/);
	if (hider) {
		console.info('开始获取隐藏章节');
		let p = Math.ceil(hider[0].match(/\d+/)[0] / 900);
		for (let i = 1; i <= p; ++i) {
			let bArr = fetch(`https://www.zhaishuyuan.com/api/`, {
				method: 'POST',
				headers: {
					'content-type': 'application/x-www-form-urlencoded'
				},
				body: `action=list&bid=${bid}&page${i}`
			}).json();
			if (bArr) Array.prototype.push.apply(baseObject.chapter, bArr.data);
		}
		console.info('成功获取隐藏章节');
	}
	baseObject.chapter = baseObject.chapter
		.sort((a, b) => (a.id < b.id ? -1 : 1))
		.map((item) => {
			item.id = '/chapter/' + bid + '/' + (item.id - bid);
			return { title: item.cN, time: item.uT, url: item.id };
		});
	console.info(`目录页解析完成,共 ${baseObject.chapter.length} 章`);
	console.info(`第一章: ${JSON.stringify(baseObject.chapter[0])}\n\n`);
}

function context(url) {
	console.info(`开始获取正文页 ${url}`);
	let response = fetch(url);
	let html = response.text();
	let document = new Document(html);
	console.info('成功获取结果');

	$ = (s) => document.select(s);
	let f = html.match(/function getDecode[^<]+/);
	if (f) {
		eval(f[0]);
		getDecode();
		console.info('成功解密内容');
	}
	baseObject.context = document.queryAllText('#content p').join(`\n　　`);
	console.info('正文解析完成');
	console.info(baseObject.context);
}

step = [(sKey) => search(sKey), () => detail(baseObject.info.origin + baseObject.search[0].url), () => chapter(baseObject.detail.url), () => context(baseObject.info.origin + baseObject.chapter[0].url)];