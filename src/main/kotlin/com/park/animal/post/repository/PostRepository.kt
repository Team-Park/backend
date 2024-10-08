package com.park.animal.post.repository

import com.park.animal.auth.entity.QUser.user
import com.park.animal.auth.entity.QUserInfo.userInfo
import com.park.animal.common.constants.OrderBy
import com.park.animal.post.dto.Coordinate
import com.park.animal.post.dto.PostDetailResponse
import com.park.animal.post.dto.PostImageResponse
import com.park.animal.post.dto.PostSummaryResponse
import com.park.animal.post.dto.SummarizedPostsByPageDto
import com.park.animal.post.entity.Post
import com.park.animal.post.entity.QPost.post
import com.park.animal.post.entity.QPostImage.postImage
import com.querydsl.core.types.Order
import com.querydsl.core.types.OrderSpecifier
import com.querydsl.core.types.Projections
import com.querydsl.jpa.JPAExpressions
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PostRepository : JpaRepository<Post, UUID>, PostQueryRepository {
    fun findByAuthor(userId: UUID): List<Post>
}

interface PostQueryRepository {
    fun findPostDetailWithImages(postId: UUID): PostDetailResponse?

    fun findSummarizedPostsByPage(size: Long, offset: Long, orderBy: OrderBy): SummarizedPostsByPageDto
}

class PostQueryRepositoryImpl(
    private val jpaQueryFactory: JPAQueryFactory,
) : PostQueryRepository {
    override fun findPostDetailWithImages(postId: UUID): PostDetailResponse? {
        val postDetail = findPostDetail(postId)
        val postImages = findPostImages(postId)

        return postDetail?.apply {
            this.imageUrls = postImages
        }
    }

    private fun findPostDetail(postId: UUID): PostDetailResponse? {
        return jpaQueryFactory.select(
            Projections.constructor(
                PostDetailResponse::class.java,
                userInfo.name,
                post.title,
                post.phoneNum,
                post.time,
                post.place,
                post.gender,
                post.gratuity,
                post.description,
                Projections.constructor(
                    Coordinate::class.java,
                    post.lat,
                    post.lng,
                ),
            ),
        )
            .from(post)
            .leftJoin(userInfo).on(userInfo.user.id.eq(post.author)).fetchJoin()
            .where(post.id.eq(postId))
            .fetchOne()
    }

    private fun findPostImages(postId: UUID): List<PostImageResponse> {
        return jpaQueryFactory.select(
            Projections.constructor(
                PostImageResponse::class.java,
                postImage.id,
                postImage.imageUrl,
            ),
        )
            .from(postImage)
            .where(postImage.post.id.eq(postId))
            .fetch()
    }

    override fun findSummarizedPostsByPage(size: Long, offset: Long, orderBy: OrderBy): SummarizedPostsByPageDto {
        val result = jpaQueryFactory
            .select(
                Projections.constructor(
                    PostSummaryResponse::class.java,
                    post.id,
                    userInfo.name,
                    post.title,
                    post.gratuity,
                    post.place,
                    post.time,
                    JPAExpressions.select(postImage.imageUrl)
                        .from(postImage)
                        .where(postImage.post.id.eq(post.id))
                        .orderBy(postImage.id.asc())
                        .limit(1),
                ),
            )
            .from(post)
            .leftJoin(user).on(post.author.eq(user.id))
            .leftJoin(userInfo).on(user.id.eq(userInfo.user.id))
            .groupBy(post.id)
            .orderBy(setOrderBy(orderBy))
            .offset(offset)
            .limit(size)
            .fetch()

        val totalCount: Long = jpaQueryFactory.select(post.count())
            .from(post)
            .fetchOne() ?: 0L

        return SummarizedPostsByPageDto(
            hasNextPage = result.size > size,
            totalCount = totalCount,
            result = result,
        )
    }

    private fun setOrderBy(orderBy: OrderBy): OrderSpecifier<*> = when (orderBy) {
        OrderBy.UPDATED_AT_DESC -> OrderSpecifier(Order.DESC, post.updatedAt)
        OrderBy.CREATED_AT_DESC -> OrderSpecifier(Order.DESC, post.createdAt)
    }
}
