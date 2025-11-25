package com.uofa.arcampusnav.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.uofa.arcampusnav.data.models.Building
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.flow

/**
 * Repository for Firebase Firestore and Realtime Database operations
 */
class FirebaseRepository {
    
    private val firestore = FirebaseFirestore.getInstance()
    private val realtimeDb = FirebaseDatabase.getInstance().reference
    
    /**
     * Get all campus buildings from Firestore
     */
    suspend fun getBuildings(): Result<List<Building>> {
        return try {
            val snapshot = firestore.collection("buildings")
                .get()
                .await()
            
            val buildings = snapshot.documents.mapNotNull { doc ->
                try {
                    Building(
                        id = doc.id,
                        name = doc.getString("name") ?: "",
                        latitude = doc.getDouble("latitude") ?: 0.0,
                        longitude = doc.getDouble("longitude") ?: 0.0,
                        address = doc.getString("address") ?: "",
                        description = doc.getString("description") ?: "",
                        rooms = (doc.get("rooms") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                        imageUrl = doc.getString("imageUrl"),
                        accessibilityFeatures = (doc.get("accessibilityFeatures") as? List<*>)
                            ?.mapNotNull { it as? String } ?: emptyList(),
                        popularTimes = (doc.get("popularTimes") as? Map<*, *>)
                            ?.mapNotNull { (k, v) -> 
                                (k as? String)?.let { key -> 
                                    (v as? Long)?.toInt()?.let { value -> key to value } 
                                }
                            }?.toMap() ?: emptyMap()
                    )
                } catch (e: Exception) {
                    null
                }
            }
            
            Result.success(buildings)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get a specific building by ID
     */
    suspend fun getBuilding(buildingId: String): Result<Building?> {
        return try {
            val doc = firestore.collection("buildings")
                .document(buildingId)
                .get()
                .await()
            
            if (doc.exists()) {
                val building = Building(
                    id = doc.id,
                    name = doc.getString("name") ?: "",
                    latitude = doc.getDouble("latitude") ?: 0.0,
                    longitude = doc.getDouble("longitude") ?: 0.0,
                    address = doc.getString("address") ?: "",
                    description = doc.getString("description") ?: "",
                    rooms = (doc.get("rooms") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                    imageUrl = doc.getString("imageUrl"),
                    accessibilityFeatures = (doc.get("accessibilityFeatures") as? List<*>)
                        ?.mapNotNull { it as? String } ?: emptyList(),
                    popularTimes = (doc.get("popularTimes") as? Map<*, *>)
                        ?.mapNotNull { (k, v) -> 
                            (k as? String)?.let { key -> 
                                (v as? Long)?.toInt()?.let { value -> key to value } 
                            }
                        }?.toMap() ?: emptyMap()
                )
                Result.success(building)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Listen to real-time crowd data updates
     */
    fun observeCrowdData(buildingId: String): Flow<Int> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val crowdLevel = snapshot.child("buildings")
                    .child(buildingId)
                    .child("currentCrowd")
                    .getValue(Int::class.java) ?: 0
                trySend(crowdLevel)
            }
            
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        
        realtimeDb.addValueEventListener(listener)
        
        awaitClose {
            realtimeDb.removeEventListener(listener)
        }
    }
    
    /**
     * Save user-generated accessibility note
     */
    suspend fun saveAccessibilityNote(
        buildingId: String,
        note: String,
        userId: String
    ): Result<Unit> {
        return try {
            val noteData = hashMapOf(
                "userId" to userId,
                "note" to note,
                "timestamp" to System.currentTimeMillis()
            )
            
            firestore.collection("buildings")
                .document(buildingId)
                .collection("accessibilityNotes")
                .add(noteData)
                .await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get accessibility notes for a building
     */
    suspend fun getAccessibilityNotes(buildingId: String): Result<List<String>> {
        return try {
            val snapshot = firestore.collection("buildings")
                .document(buildingId)
                .collection("accessibilityNotes")
                .orderBy("timestamp")
                .limit(10)
                .get()
                .await()
            
            val notes = snapshot.documents.mapNotNull { 
                it.getString("note") 
            }
            
            Result.success(notes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Update building crowd level (for admin/sensor integration)
     */
    suspend fun updateCrowdLevel(buildingId: String, level: Int): Result<Unit> {
        return try {
            realtimeDb.child("buildings")
                .child(buildingId)
                .child("currentCrowd")
                .setValue(level)
                .await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
